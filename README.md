mirror-music is a tool written in Clojure for copying tracks off
your iPod/iPhone.  A nice thing about running on the JVM is it works
on Linux, OSX and Windows too.

There's actually nothing very iPod-specific about any of this: I don't
attempt to parse the iTunes database or anything clever like that.
Instead, mirror-music just walks over all the MP3 files on the iPod's
filesystem and uses their ID3 data to build up a mapping of
artist/album/title of each track to its file path on the iPod.


## Building it

The usual steps:

  1.  Get Leiningen from http://github.com/technomancy/leiningen and put
      the 'lein' script somewhere in your $PATH.

  2.  Run 'lein uberjar'.  Lein will grab all required dependencies
      and produce a 'mirror-music-[VERSION]-standalone.jar'.  I rename
      this to mirror-music.jar.


## Running it

To use it, you build a list of what's on your iPod:

    java -jar mirror-music.jar list /my/ipod /my/ipod/mirror-music-list.txt

then to get stuff off your iPod:

    cd /some/other/dir
    java -jar mirror-music.jar get /my/ipod /my/ipod/mirror-music-list.txt "Cloudkicker.*Beacons"

And you'll see something like:

    $ java -jar mirror-music.jar get /my/ipod /my/ipod/mirror/list.txt "Cloudkicker.*Beacons"
    ipod_control/music/f03/g0_now__i_was_scared_.mp3  ->  Cloudkicker/Beacons/(Cloudkicker) I Admit It Now. I Was Scared..mp3 ...
    ipod_control/music/f36/g0__we_re_going_down_.mp3  ->  Cloudkicker/Beacons/(Cloudkicker) We're Goin' In. We're Going Down..mp3 ...
    ipod_control/music/f49/g0_t_wide_open_field_.mp3  ->  Cloudkicker/Beacons/(Cloudkicker) ...it's Just Wide-Open Field..mp3 ...
    ipod_control/music/f00/g0_a_minute__damn_it_.mp3  ->  Cloudkicker/Beacons/(Cloudkicker) Here, Wait A Minute! Damn It!.mp3 ...
    ipod_control/music/f47/g0_udkicker__untitled.mp3  ->  Cloudkicker/Beacons/(Cloudkicker) Untitled.mp3 ...
    ipod_control/music/f22/g0___amy__i_love_you_.mp3  ->  Cloudkicker/Beacons/(Cloudkicker) Amy, I Love You..mp3 ...
    ipod_control/music/f11/g0_going_to_invert___.mp3  ->  Cloudkicker/Beacons/(Cloudkicker) We Are Going To Invert....mp3 ...
    ipod_control/music/f38/g0_r__push_it_way_up_.mp3  ->  Cloudkicker/Beacons/(Cloudkicker) Push It Way Up!.mp3 ...
    ipod_control/music/f28/g0_udkicker__oh__god_.mp3  ->  Cloudkicker/Beacons/(Cloudkicker) Oh, God..mp3 ...
    ipod_control/music/f00/g0_e_were_all_scared_.mp3  ->  Cloudkicker/Beacons/(Cloudkicker) We Were All Scared..mp3 ...
    ipod_control/music/f42/g0___man__we_are_hit_.mp3  ->  Cloudkicker/Beacons/(Cloudkicker) It's Bad. We're Hit, Man, We Are Hit..mp3 ...

    $ tree Cloudkicker 
    Cloudkicker
    `-- Beacons
        |-- (Cloudkicker) ...it's Just Wide-Open Field..mp3
        |-- (Cloudkicker) Amy, I Love You..mp3
        |-- (Cloudkicker) Here, Wait A Minute! Damn It!.mp3
        |-- (Cloudkicker) I Admit It Now. I Was Scared..mp3
        |-- (Cloudkicker) It's Bad. We're Hit, Man, We Are Hit..mp3
        |-- (Cloudkicker) Oh, God..mp3
        |-- (Cloudkicker) Push It Way Up!.mp3
        |-- (Cloudkicker) Untitled.mp3
        |-- (Cloudkicker) We Are Going To Invert....mp3
        |-- (Cloudkicker) We Were All Scared..mp3
        |-- (Cloudkicker) We're Goin' In. We're Going Down..mp3
        `-- playlists
            `-- (Cloudkicker) Beacons.m3u


It works for other music besides Cloudkicker, but you can name your
price for this album at http://cloudkicker.bandcamp.com/, so you
should probably get it just in case.  It's a good album.  Trust me.

Walking the iPod and reading the ID3 data of every file is quite slow,
so I keep my list up to date with:

    java -jar mirror-music.jar list /my/ipod /my/ipod/mirror-music-list.txt 10

This just adds any MP3s that were added in the past 10 days, and takes
care of zapping any now-deleted files from the list.  This only takes
a few seconds.
