import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * CSDS 233 - Programming Assignment 1
 * Interactive Music Sequencer: the menu-driven driver.
 *
 * <p>Phrases are stored in a {@link PhraseList} (a doubly linked list with a
 * current cursor) and played with the instructor's {@link MidiPlayer}. Every
 * phrase is checked by {@link PhraseValidator} before it is stored, so
 * playback can never hit malformed input.</p>
 *
 * <p>The driver never crashes on bad input: non-numeric or empty menu
 * choices, invalid indices (IndexOutOfBoundsException is caught and reported)
 * and end of input (EOF) are all handled.</p>
 *
 * <pre>
 *   java Main                    interactive
 *   java Main --echo &lt; demo.txt   scripted run; echoes each line read
 * </pre>
 */
public class Main {

    private static final String PHRASE_HELP =
            "Phrase format: PITCH:DURATION tokens separated by spaces, e.g. C4:0.5 E4:0.5 G4:1.0\n"
            + "  Pitch: A-G, optional # or b, octave 0-9 (C4, D#4, Eb5); R = rest; chords: C4+E4+G4:1.0\n"
            + "  Duration: positive decimal (0.25, 0.5, 1.0, ...)";

    private final PhraseList<String> list = new PhraseList<String>();
    private final BufferedReader in;
    private final MidiPlayer player;
    private final boolean echoInput;
    private boolean inputClosed = false;
    private boolean playbackFailed = false;

    Main(BufferedReader in, MidiPlayer player, boolean echoInput) {
        this.in = in;
        this.player = player;
        this.echoInput = echoInput;
    }

    public static void main(String[] args) {
        boolean echo = args.length > 0 && "--echo".equals(args[0]);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        MidiPlayer player = null;
        try {
            player = new MidiPlayer();
            new Main(in, player, echo).run();
        } finally {
            if (player != null) {
                player.close();
            }
            System.out.println("MIDI system closed. Goodbye!");
        }
    }

    /**
     * Clears {@code list} and fills it with the demo composition
     * ("Twinkle, Twinkle, Little Star" plus a closing chord cadence).
     * Package-private and static so the headless tests can use it.
     *
     * @param list the list to fill
     */
    static void loadDemoComposition(PhraseList<String> list) {
        list.clear();
        list.insertLast("C4:0.5 C4:0.5 G4:0.5 G4:0.5");                // Twinkle, twinkle
        list.insertLast("A4:0.5 A4:0.5 G4:1.0");                       // little star
        list.insertLast("F4:0.5 F4:0.5 E4:0.5 E4:0.5");                // How I wonder
        list.insertLast("D4:0.5 D4:0.5 C4:1.0");                       // what you are
        list.insertLast("F4+A4+C5:0.5 G4+B4+D5:0.5 C4+E4+G4:1.0");     // IV - V - I cadence
    }

    // ------------------------------------------------------------------
    // Menu loop
    // ------------------------------------------------------------------

    void run() {
        printBanner();
        while (true) {
            printMenu();
            String line = readLine("Choose an option [0-15]: ");
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.isEmpty()) {
                warn("Please type a menu number (0-15).");
                continue;
            }
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("q")) {
                line = "0";
            }
            Integer choice = parseInt(line);
            if (choice == null) {
                warn("'" + line + "' is not a menu number. Please type 0-15.");
                continue;
            }
            if (choice == 0) {
                System.out.println("Exiting the sequencer...");
                return;
            }
            try {
                handle(choice);
            } catch (IndexOutOfBoundsException e) {
                warn(e.getMessage() + ". The list was not changed.");
            } catch (RuntimeException e) {
                warn("Operation failed: " + e);
            }
            if (inputClosed) {
                break;
            }
        }
        System.out.println("Input closed (end of file). Exiting the sequencer...");
    }

    private void handle(int choice) {
        switch (choice) {
            case 1:  insertFront(); break;
            case 2:  insertBack(); break;
            case 3:  insertAfterCurrent(); break;
            case 4:  insertAtIndex(); break;
            case 5:  repeatCurrent(); break;
            case 6:  removeCurrent(); break;
            case 7:  removeAtIndex(); break;
            case 8:  movePhrase(); break;
            case 9:  playAll(); break;
            case 10: playFromCurrent(); break;
            case 11: playCurrent(); break;
            case 12: forward(); break;
            case 13: backward(); break;
            case 14: showList(); break;
            case 15: loadDemo(); break;
            default: warn("There is no option " + choice + ". Please type 0-15.");
        }
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    private void insertFront() {
        String phrase = readPhrase();
        if (phrase == null) {
            return;
        }
        list.insertFirst(phrase);
        showChange("Inserted at the front: " + phrase);
    }

    private void insertBack() {
        String phrase = readPhrase();
        if (phrase == null) {
            return;
        }
        list.insertLast(phrase);
        showChange("Inserted at the back: " + phrase);
    }

    private void insertAfterCurrent() {
        String phrase = readPhrase();
        if (phrase == null) {
            return;
        }
        boolean wasEmpty = list.isEmpty();
        list.insertAfterCurrent(phrase);
        showChange(wasEmpty
                ? "The list was empty: the phrase is now the only node and the current one."
                : "Inserted after the current phrase: " + phrase);
    }

    private void insertAtIndex() {
        String phrase = readPhrase();
        if (phrase == null) {
            return;
        }
        Integer index = readIndex("Insert at index (0.." + list.size() + "): ");
        if (index == null) {
            return;
        }
        list.insertAt(index, phrase);
        showChange("Inserted at index " + index + ": " + phrase);
    }

    private void repeatCurrent() {
        if (list.repeatCurrent()) {
            showChange("Appended a copy of the current phrase to the end.");
        } else {
            warn("The list is empty: there is no current phrase to repeat.");
        }
    }

    private void removeCurrent() {
        if (list.isEmpty()) {
            warn("The list is empty: nothing to remove.");
            return;
        }
        String removed = list.removeCurrent();
        showChange("Removed the current phrase: " + removed);
    }

    private void removeAtIndex() {
        if (list.isEmpty()) {
            warn("The list is empty: nothing to remove.");
            return;
        }
        Integer index = readIndex("Index to remove (0.." + (list.size() - 1) + "): ");
        if (index == null) {
            return;
        }
        String removed = list.removeAt(index);
        showChange("Removed the phrase at index " + index + ": " + removed);
    }

    private void movePhrase() {
        if (list.isEmpty()) {
            warn("The list is empty: nothing to move.");
            return;
        }
        String range = "(0.." + (list.size() - 1) + ")";
        Integer source = readIndex("Source index " + range + ": ");
        if (source == null) {
            return;
        }
        Integer target = readIndex("Target index, i.e. final position " + range + ": ");
        if (target == null) {
            return;
        }
        if (list.move(source, target)) {
            showChange("Moved the node at index " + source + " to index " + target + " (links rewired, nothing copied).");
        } else {
            System.out.println("Source and target are the same: nothing to move.");
            showState();
        }
    }

    private void loadDemo() {
        int replaced = list.size();
        loadDemoComposition(list);
        showChange("Loaded the demo composition: " + list.size() + " phrases"
                + (replaced > 0 ? " (replaced " + replaced + " existing)." : "."));
    }

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    private void playAll() {
        if (list.isEmpty()) {
            warn("Nothing to play: the list is empty.");
            return;
        }
        System.out.println("Playing all " + list.size() + " phrase(s) from the head...");
        playbackFailed = false;
        list.traverseAll(this::playNode);
        if (!playbackFailed) {
            System.out.println("Playback finished.");
        }
    }

    private void playFromCurrent() {
        if (list.isEmpty()) {
            warn("Nothing to play: the list is empty (there is no current phrase).");
            return;
        }
        System.out.println("Playing from the current phrase to the tail...");
        playbackFailed = false;
        list.traverseFromCurrent(this::playNode);
        if (!playbackFailed) {
            System.out.println("Playback finished.");
        }
    }

    private void playCurrent() {
        if (list.isEmpty()) {
            warn("Nothing to play: the list is empty (there is no current phrase).");
            return;
        }
        playNode(list.getCurrentIndex(), list.getCurrent());
    }

    /** Plays one phrase; returns false (stop) if the MIDI system fails. */
    private boolean playNode(int index, String phrase) {
        System.out.println("Playing node [" + index + "]: " + phrase);
        try {
            player.play(phrase);
            return true;
        } catch (RuntimeException e) {
            playbackFailed = true;
            warn("Playback failed (" + e + "). Is a MIDI synthesizer available? Playback stopped.");
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Navigation and display
    // ------------------------------------------------------------------

    private void forward() {
        if (list.isEmpty()) {
            warn("The list is empty: there is no current phrase.");
        } else if (list.moveForward()) {
            showChange("Current moved forward.");
        } else {
            warn("Already at the last phrase (tail): cannot move forward.");
            System.out.println(list.status());
        }
    }

    private void backward() {
        if (list.isEmpty()) {
            warn("The list is empty: there is no current phrase.");
        } else if (list.moveBackward()) {
            showChange("Current moved backward.");
        } else {
            warn("Already at the first phrase (head): cannot move backward.");
            System.out.println(list.status());
        }
    }

    private void showList() {
        showState();
        System.out.println(list.backwardTrace());
        if (!list.isEmpty()) {
            final int currentIndex = list.getCurrentIndex();
            list.traverseAll((index, phrase) -> {
                System.out.println("  [" + index + "] " + phrase + (index == currentIndex ? "   <-- current" : ""));
                return true;
            });
        }
    }

    private void showChange(String message) {
        System.out.println(message);
        showState();
    }

    private void showState() {
        System.out.println("List: " + list);
        System.out.println(list.status());
    }

    private void printBanner() {
        System.out.println("=================================================");
        System.out.println("  CSDS 233 PA1: Interactive Music Sequencer");
        System.out.println("=================================================");
        System.out.println(PHRASE_HELP);
    }

    private void printMenu() {
        System.out.println();
        System.out.println("------------------- MENU -------------------");
        System.out.println(" 1) Insert phrase at front      9) Play all");
        System.out.println(" 2) Insert phrase at back      10) Play from current");
        System.out.println(" 3) Insert after current       11) Play current");
        System.out.println(" 4) Insert at index            12) Move current forward");
        System.out.println(" 5) Repeat current (to end)    13) Move current backward");
        System.out.println(" 6) Remove current             14) Show list");
        System.out.println(" 7) Remove at index            15) Load demo composition");
        System.out.println(" 8) Move phrase (src -> tgt)    0) Exit");
        System.out.println("--------------------------------------------");
    }

    private static void warn(String message) {
        System.out.println("[!] Warning: " + message);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /** Prints the prompt and reads one line; null (and inputClosed) on EOF. */
    private String readLine(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        String line;
        try {
            line = in.readLine();
        } catch (IOException e) {
            line = null;
        }
        if (line == null) {
            inputClosed = true;
            System.out.println();
            return null;
        }
        if (echoInput) {
            System.out.println(line);
        }
        return line;
    }

    /** Reads and validates a phrase; null if EOF or invalid (a warning is printed). */
    private String readPhrase() {
        String raw = readLine("Enter phrase (e.g. C4:0.5 E4:0.5 G4:1.0): ");
        if (raw == null) {
            return null;
        }
        String error = PhraseValidator.validate(raw);
        if (error != null) {
            warn("Phrase rejected, " + error + ". Nothing was inserted.");
            return null;
        }
        return PhraseValidator.normalize(raw);
    }

    /** Reads a whole number; null if EOF, empty or not a number (a warning is printed). */
    private Integer readIndex(String prompt) {
        String raw = readLine(prompt);
        if (raw == null) {
            return null;
        }
        raw = raw.trim();
        if (raw.isEmpty()) {
            warn("No index entered. Operation cancelled.");
            return null;
        }
        Integer value = parseInt(raw);
        if (value == null) {
            warn("'" + raw + "' is not a valid whole number. Operation cancelled.");
        }
        return value;
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
