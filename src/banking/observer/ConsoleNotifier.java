package banking.observer;

public class ConsoleNotifier implements AccountObserver {
    @Override
    public void update(String message) {
        System.out.println("NOTIFICATION: " + message);
    }
}
