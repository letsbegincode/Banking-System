package banking.ui.console;

import java.util.Scanner;

public class ConsoleIO {
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String BG_BLUE = "\u001B[44m";
    private static final String BOLD = "\u001B[1m";
    private static final String UNDERLINE = "\u001B[4m";

    private final Scanner scanner;

    public ConsoleIO() {
        this(new Scanner(System.in));
    }

    public ConsoleIO(Scanner scanner) {
        this.scanner = scanner;
    }

    public void showWelcomeBanner(String title) {
        println(BG_BLUE + WHITE + BOLD);
        println("╔" + "═".repeat(46) + "╗");
        println("║" + " ".repeat(46) + "║");
        println("║" + center(title.toUpperCase(), 46) + "║");
        println("║" + " ".repeat(46) + "║");
        println("╚" + "═".repeat(46) + "╝" + RESET);
        println("");
    }

    public void heading(String title) {
        println(PURPLE + BOLD + "\n===== " + title + " =====" + RESET);
    }

    public void subHeading(String title) {
        println(CYAN + BOLD + "\n--- " + title + " ---" + RESET);
    }

    public void info(String message) {
        println(CYAN + message + RESET);
    }

    public void success(String message) {
        println(GREEN + message + RESET);
    }

    public void warning(String message) {
        println(YELLOW + message + RESET);
    }

    public void error(String message) {
        println(RED + message + RESET);
    }

    public void println(String message) {
        System.out.println(message);
    }

    public void print(String message) {
        System.out.print(message);
    }

    public String prompt(String prompt) {
        print(YELLOW + prompt + RESET);
        return scanner.nextLine();
    }

    public int promptInt(String prompt) {
        while (true) {
            String input = prompt(prompt);
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                error("Please enter a valid number.");
            }
        }
    }

    public double promptDouble(String prompt) {
        while (true) {
            String input = prompt(prompt);
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                error("Please enter a valid number.");
            }
        }
    }

    public void printTableHeader(String format, Object... values) {
        println(CYAN + String.format(format, values) + RESET);
    }

    public void printlnBold(String label, Object value) {
        println(BOLD + label + RESET + value);
    }

    public String underline(String message) {
        return UNDERLINE + message + RESET;
    }

    public void close() {
        scanner.close();
    }

    private String center(String value, int width) {
        if (value.length() >= width) {
            return value.substring(0, width);
        }
        int padding = width - value.length();
        int padStart = padding / 2;
        int padEnd = padding - padStart;
        return " ".repeat(padStart) + value + " ".repeat(padEnd);
    }
}
