import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.MidiChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * CSDS 233 - Programming Assignment 1
 * A self-contained, zero-dependency MIDI player for Java.
 * Supports chords using the '+' connector (e.g. "C4+E4+G4:1.0").
 */
public class MidiPlayer {
    private Synthesizer synthesizer;
    private MidiChannel channel;
    private static final Map<String, Integer> NOTE_OFFSETS = new HashMap<>();

    static {
        NOTE_OFFSETS.put("C", 0);
        NOTE_OFFSETS.put("D", 2);
        NOTE_OFFSETS.put("E", 4);
        NOTE_OFFSETS.put("F", 5);
        NOTE_OFFSETS.put("G", 7);
        NOTE_OFFSETS.put("A", 9);
        NOTE_OFFSETS.put("B", 11);
    }

    public MidiPlayer() {
        try {
            this.synthesizer = MidiSystem.getSynthesizer();
            this.synthesizer.open();
            this.channel = this.synthesizer.getChannels()[0];
            this.channel.programChange(0); // Standard Grand Piano
        } catch (Exception e) {
            System.err.println("Error initializing MIDI Synthesizer: " + e.getMessage());
        }
    }

    public void play(String phrase) {
        if (phrase == null || phrase.trim().isEmpty()) {
            return;
        }

        String[] tokens = phrase.trim().split("\\s+");
        for (String token : tokens) {
            String[] parts = token.split(":");
            if (parts.length != 2) {
                System.err.println("Malformed note token: " + token);
                continue;
            }

            String pitchToken = parts[0];
            double duration = Double.parseDouble(parts[1]);
            int durationMs = (int) (duration * 1000);

            // Split the pitch to extract chord notes
            String[] subPitches = pitchToken.split("\\+");
            int[] midiNotes = new int[subPitches.length];
            for (int i = 0; i < subPitches.length; i++) {
                midiNotes[i] = parsePitchToMidi(subPitches[i]);
            }

            // Check if it is a rest
            boolean isRest = true;
            for (int note : midiNotes) {
                if (note != -1) {
                    isRest = false;
                    break;
                }
            }

            if (isRest) {
                try {
                    Thread.sleep(durationMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                // Fire all notes in the chord simultaneously
                for (int note : midiNotes) {
                    if (note != -1) {
                        channel.noteOn(note, 80);
                    }
                }

                try {
                    Thread.sleep(durationMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Turn off all notes in the chord simultaneously
                for (int note : midiNotes) {
                    if (note != -1) {
                        channel.noteOff(note);
                    }
                }
            }
        }
    }

    private int parsePitchToMidi(String pitch) {
        String cleanPitch = pitch.toUpperCase().trim();
        if (cleanPitch.startsWith("R")) {
            return -1;
        }

        char baseNote = cleanPitch.charAt(0);
        if (!NOTE_OFFSETS.containsKey(String.valueOf(baseNote))) {
            return -1;
        }

        int offset = NOTE_OFFSETS.get(String.valueOf(baseNote));
        int accidental = 0;
        int octaveIndex = 1;

        if (cleanPitch.length() > 1) {
            char nextChar = cleanPitch.charAt(1);
            if (nextChar == '#') {
                accidental = 1;
                octaveIndex = 2;
            } else if (nextChar == 'B' || nextChar == '♭') {
                accidental = -1;
                octaveIndex = 2;
            }
        }

        int octave = 4;
        if (octaveIndex < cleanPitch.length()) {
            try {
                octave = Integer.parseInt(cleanPitch.substring(octaveIndex));
            } catch (NumberFormatException e) {
                // Fall back to default octave
            }
        }

        return (octave + 1) * 12 + offset + accidental;
    }

    public void close() {
        if (this.synthesizer != null && this.synthesizer.isOpen()) {
            this.synthesizer.close();
        }
    }
}
