(ns mirror-music
  (:gen-class :name MirrorMusic
              :main true)
  (:import (org.apache.commons.io FilenameUtils FileUtils)
           (java.io File FilenameFilter ByteArrayInputStream)
           (java.util.logging LogManager)
           (org.jaudiotagger.audio AudioFile AudioFileIO)
           (org.jaudiotagger.tag FieldKey)
           (java.util.regex Pattern))
  (:use [clojure.contrib.duck-streams :exclude [spit]]
        clojure.contrib.str-utils
        [clojure.contrib.io :only [file]]))


(System/setProperty "file.encoding" "UTF-8")

(def path-separator "\1")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn normalise
  "Normalise a path (removing unnecessary dots and slashes).  For directories,
yields a string with a trailing separator."
  [path]
  (FilenameUtils/normalize (str path
                                (if (.isDirectory (file path))
                                  File/separator
                                  ""))))


(defn clean
  "Clean a path name by removing doubled extensions (a common mistake) and,
for Windows filesystems, characters likely to cause problems."
  [s]
  (let [s (.replaceAll s "\\.mp3\\.mp3" ".mp3")]
    (if (not= File/separator "/")
      ;; Alert!  Children's operating system!
      (.replaceAll s "[^ \\x80-\\xFFA-Za-z0-9!#$%&'()\\-@^_`{}~_+,\\.;=\\[\\]\\\\]" "")
      s)))


(defn as-integer
  "Parse a string as an integer, returning zero if that fails."
  [s]
  (try (Integer/valueOf s)
       (catch NumberFormatException _
         0)))


(defn format-track-name [artist title extension]
  (format "(%s) %s.%s" artist title extension))


(defn mp3? [f]
  (re-find #"(?i)\.mp3$" (.getName f)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; ID3 manipulation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Turn off jaudiotagger console logging.
(.readConfiguration
 (LogManager/getLogManager)
 (ByteArrayInputStream. (.getBytes "org.jaudiotagger.level = OFF")))


(defn id3-data [path]
  (let [mp3 (AudioFileIO/read path)]
    (into {} (map (fn [[attr field]]
                    [attr (try (.. mp3 getTag (getFirst field))
                               (catch Exception _ "Untitled"))])
                  {:title FieldKey/TITLE
                   :artist FieldKey/ARTIST
                   :track FieldKey/TRACK
                   :album FieldKey/ALBUM}))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; List file management
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn encode-file-separators [path]
  (.replace path File/separator path-separator))


(defn decode-file-separators [path]
  (.replace path path-separator File/separator))


(defn parse [line]
  (let [[dest source] (rest (first (re-seq #"^(.*\.mp3) -> (.*\.mp3)$" line)))]
    [(clean (decode-file-separators dest)) (decode-file-separators source)]))


(defn guess-music-dir
  "Try to find the directory containing the actual MP3 files.
Varies from generation to generation."
  [mountpoint]
  (or (first (for [control ["ipod_control" "itunes_control"
                            "iPod_Control" "iTunes_Control"]
                   music ["music" "Music"]
                   :let [file (file mountpoint control music)]
                   :when (.exists file)]
               file))
      (throw (Exception. (str "Couldn't guess the music directory for: "
                              mountpoint)))))


(defn find-tracks
  "Find all tracks on `mountpoint' modified within the last `days' days.
Returns a seq of [originalpath ipodpath]"
  [mountpoint days]
  (for [f (file-seq (guess-music-dir mountpoint))
        :when (and (mp3? f)
                   (or (nil? days)
                       (< (- (System/currentTimeMillis)
                             (.lastModified f))
                          (* days 24 60 60 1000))))]
    (let [id3 (id3-data f)]
      [(.getPath (file (:artist id3)
                       (:album id3)
                       (format-track-name (:artist id3) (:title id3) "mp3")))
       (.substring (normalise (.getPath f))
                   (count mountpoint))])))


(defn scrub-deletes
  "Remove any now-deleted files from `list-file'."
  [mountpoint list-file]
  (with-open [in (reader list-file)
              out (writer (str list-file "tmp"))]
    (doseq [line (filter (fn [entry]
                           (let [[name source] (parse entry)]
                             (.exists (file mountpoint source))))
                         (line-seq in))]
      (.println out line)))
  (.renameTo (file (str list-file "tmp")) (file list-file)))


(defn list-ipod [mountpoint outfile days]
  "Find all tracks on an iPod that were written within the last `days' days.
Writes a mapping from the original path and filename to the path on the iPod's
filesystem to `outfile'."
  (with-open [out (append-writer outfile)]
    (doseq [track (find-tracks mountpoint days)]
      (.println out (format "%s -> %s"
                            (encode-file-separators (nth track 0))
                            (encode-file-separators (nth track 1))))))
  (when days
    ;; Update the existing file to zap any deletes too.
    (scrub-deletes mountpoint outfile)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; File extraction
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn build-playlists
  "Generate M3U files for the MP3 files extracted to `dirs'."
  [dirs]
  (doseq [dir dirs]
    (let [[artist album] (.split dir (str "\\Q" File/separator "\\E"))
          playlists (file dir "playlists")
          mp3s (sort #(compare (as-integer (%1 :track))
                               (as-integer (%2 :track)))
                     (map #(assoc (id3-data %) :filename (.getName %))
                          (filter mp3? (.listFiles (file dir)))))]
      (.mkdirs playlists)
      (with-open [pl (writer (file playlists
                                   (format-track-name artist album "m3u")))]
        (doseq [mp3 mp3s]
          (let [id3-name (format-track-name (:artist mp3) (:title mp3) "mp3")
                comment (when (not= id3-name (:filename mp3))
                          (format "## ..%s%s\r\n" File/separator id3-name))]
            (when comment
              (.print pl comment))
            (.print pl (format "..%s%s\r\n"
                               File/separator
                               (:filename mp3)))))))))


(defn extract-tracks
  "Find tracks in `list-file' matching the regexp `pattern' and copy them to the
current directory."
  [mountpoint list-file pattern]
  (let [pattern (re-pattern pattern)
        dirs (atom #{})]
    (doseq [line (line-seq (reader list-file))]
      (let [[dest source] (parse line)]
        (when (re-find pattern dest)
          (.mkdirs (file (FilenameUtils/getPath dest)))
          (swap! dirs conj (FilenameUtils/getPath dest))
          (print source " -> " dest "...")
          (try
            (let [source (file mountpoint source)
                  dest (file dest)]
              (when (or (not (.exists dest))
                        (not= (.length source) (.length dest)))
                (FileUtils/copyFile source dest)))
            (catch Exception e (.println System/err e)))
          (println ""))))
    (build-playlists @dirs)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Main program
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main [& args]
  (when (empty? args)
    (println "Usage: [me] list <ipod mountpoint> <output file> [mtime days]")
    (println "       [me] get <ipod mountpoint> <list file> [pattern]")
    (System/exit 0))
  (cond (= (nth args 0) "list")
        (let [[_ mountpoint outfile & [mtime]] args]
          (list-ipod (normalise mountpoint) outfile (and mtime (Integer. mtime))))

        (= (nth args 0) "get")
        (let [[_ mountpoint listfile & [pattern]] args]
          (extract-tracks (normalise mountpoint)
                          listfile
                          (or pattern ".*")))))
