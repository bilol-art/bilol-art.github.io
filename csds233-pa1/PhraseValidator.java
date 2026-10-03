/**
 * CSDS 233 - Programming Assignment 1
 *
 * Checks a phrase before it is stored in the list. {@code MidiPlayer.play()}
 * calls {@code Double.parseDouble} on every duration and would throw a
 * NumberFormatException (crashing the sequencer) on input such as
 * {@code C4:abc}, so every phrase is validated first.
 *
 * <p>Grammar (tokens separated by whitespace):</p>
 * <pre>
 *   token    = pitches ":" duration
 *   pitches  = "R" | pitch { "+" pitch }          (R = rest, "+" = chord)
 *   pitch    = letter [ "#" | "b" ] octave        e.g. C4, D#4, Eb5
 *   letter   = "A" .. "G"
 *   octave   = "0" .. "9"   (and the note must be within MIDI 0..127)
 *   duration = positive decimal number, at most MAX_DURATION, e.g. 1, 0.5, .25
 * </pre>
 *
 * <p>The input is scanned character by character, with no split() and no
 * regular expressions.</p>
 */
public final class PhraseValidator {

    /**
     * Longest duration accepted for a single token. MidiPlayer sleeps for
     * duration * 1000 ms, so a typo such as 10000 would freeze the program
     * for hours.
     */
    public static final double MAX_DURATION = 60.0;

    private PhraseValidator() {
    }

    /**
     * Validates a phrase.
     *
     * @param phrase the text typed by the user
     * @return null if the phrase is valid, otherwise a message that names the
     *         offending token and explains what is wrong with it
     */
    public static String validate(String phrase) {
        if (phrase == null || phrase.trim().isEmpty()) {
            return "the phrase is empty (expected tokens like C4:1.0 E4:0.5)";
        }
        int tokenNumber = 0;
        int i = 0;
        int n = phrase.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(phrase.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            while (i < n && !Character.isWhitespace(phrase.charAt(i))) {
                i++;
            }
            tokenNumber++;
            String token = phrase.substring(start, i);
            String problem = checkToken(token);
            if (problem != null) {
                return "token " + tokenNumber + " '" + token + "' is invalid: " + problem;
            }
        }
        return null;
    }

    /**
     * @param phrase the text to check
     * @return true if {@link #validate(String)} finds no problem
     */
    public static boolean isValid(String phrase) {
        return validate(phrase) == null;
    }

    /**
     * Trims a phrase and collapses runs of whitespace into single spaces,
     * e.g. {@code "  C4:1.0    E4:1.0 "} becomes {@code "C4:1.0 E4:1.0"}.
     *
     * @param phrase a phrase (normally already validated)
     * @return the normalized phrase, or "" for null
     */
    public static String normalize(String phrase) {
        if (phrase == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean pendingSpace = false;
        for (int i = 0; i < phrase.length(); i++) {
            char c = phrase.charAt(i);
            if (Character.isWhitespace(c)) {
                pendingSpace = sb.length() > 0;
            } else {
                if (pendingSpace) {
                    sb.append(' ');
                    pendingSpace = false;
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** @return null if the token is valid, otherwise what is wrong with it */
    static String checkToken(String token) {
        int colon = token.indexOf(':');
        if (colon < 0) {
            return "missing ':' between pitch and duration (expected PITCH:DURATION, e.g. C4:1.0)";
        }
        if (token.indexOf(':', colon + 1) >= 0) {
            return "it has more than one ':' (expected PITCH:DURATION, e.g. C4:1.0)";
        }
        String pitches = token.substring(0, colon);
        String duration = token.substring(colon + 1);
        if (pitches.isEmpty()) {
            return "the pitch before ':' is missing";
        }
        if (duration.isEmpty()) {
            return "the duration after ':' is missing";
        }
        String problem = checkPitches(pitches);
        if (problem != null) {
            return problem;
        }
        return checkDuration(duration);
    }

    /** Checks "R", a single pitch, or a chord such as "C4+E4+G4". */
    private static String checkPitches(String pitches) {
        if (pitches.equals("R")) {
            return null;
        }
        int start = 0;
        while (true) {
            int plus = pitches.indexOf('+', start);
            int end = plus < 0 ? pitches.length() : plus;
            String pitch = pitches.substring(start, end);
            if (pitch.isEmpty()) {
                return "the chord '" + pitches + "' has an empty note (write chords like C4+E4+G4)";
            }
            if (pitch.equals("R")) {
                return "a rest (R) cannot be part of a chord";
            }
            String problem = checkPitch(pitch);
            if (problem != null) {
                return problem;
            }
            if (plus < 0) {
                return null;
            }
            start = plus + 1;
        }
    }

    /** Checks one pitch such as C4, D#4 or Eb5. */
    private static String checkPitch(String pitch) {
        char letter = pitch.charAt(0);
        int offset = semitoneOffset(letter);
        if (offset < 0) {
            if (semitoneOffset(Character.toUpperCase(letter)) >= 0) {
                return "pitch '" + pitch + "' must use an uppercase note letter (e.g. C4, not c4)";
            }
            return "pitch '" + pitch + "' must start with a note letter A-G (or be R for a rest)";
        }
        int pos = 1;
        int accidental = 0;
        if (pos < pitch.length() && (pitch.charAt(pos) == '#' || pitch.charAt(pos) == 'b')) {
            accidental = pitch.charAt(pos) == '#' ? 1 : -1;
            pos++;
        }
        if (pos >= pitch.length()) {
            return "pitch '" + pitch + "' is missing its octave number (e.g. C4)";
        }
        char octaveChar = pitch.charAt(pos);
        if (pos != pitch.length() - 1 || octaveChar < '0' || octaveChar > '9') {
            return "pitch '" + pitch + "' needs a single octave digit 0-9 after the note"
                    + " (use # or b for sharps and flats, e.g. D#4, Eb5)";
        }
        int octave = octaveChar - '0';
        int midi = (octave + 1) * 12 + offset + accidental;
        if (midi > 127) {
            return "pitch '" + pitch + "' is above the MIDI range (highest note is G9)";
        }
        return null;
    }

    /** Accepts digits with at most one decimal point; the value must be in (0, MAX_DURATION]. */
    private static String checkDuration(String duration) {
        int digits = 0;
        int dots = 0;
        for (int i = 0; i < duration.length(); i++) {
            char c = duration.charAt(i);
            if (c >= '0' && c <= '9') {
                digits++;
            } else if (c == '.') {
                dots++;
            } else {
                digits = -1;
                break;
            }
        }
        if (digits <= 0 || dots > 1) {
            return "duration '" + duration + "' is not a positive decimal number (e.g. 0.5 or 1.0)";
        }
        double value = Double.parseDouble(duration);
        if (value <= 0.0) {
            return "duration '" + duration + "' must be greater than 0";
        }
        if (value > MAX_DURATION) {
            return "duration '" + duration + "' is too long (maximum is " + MAX_DURATION + ")";
        }
        return null;
    }

    /** @return semitones above C for A-G, or -1 for any other character */
    private static int semitoneOffset(char letter) {
        switch (letter) {
            case 'C': return 0;
            case 'D': return 2;
            case 'E': return 4;
            case 'F': return 5;
            case 'G': return 7;
            case 'A': return 9;
            case 'B': return 11;
            default:  return -1;
        }
    }
}
