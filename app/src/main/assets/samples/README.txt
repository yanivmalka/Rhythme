Instrument samples for the held note (piano, guitar, drum, violin, clarinet).

Source: FluidR3_GM soundfont by Frank Wen, as rendered to single notes in
https://github.com/gleitz/midi-js-soundfonts (FluidR3_GM folder).
License: Creative Commons Attribution 3.0 (https://creativecommons.org/licenses/by/3.0/us/).

Changes made for this app: converted to 22.05 kHz mono WAV, leading silence removed,
level normalized, decaying notes trimmed, and sustained notes (violin, clarinet) cut at a
loop point with a baked crossfade so they can be held for any length.

index.txt: one line per file: instrument, MIDI note, loop start, loop end (in samples;
0 0 = plays once), file name.
