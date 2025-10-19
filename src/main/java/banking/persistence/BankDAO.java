package banking.persistence;

import banking.service.Bank;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public final class BankDAO {
    private static final String FILENAME = "banking_system.ser";

    private BankDAO() {
    }

    public static void saveBank(Bank bank) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILENAME))) {
            oos.writeObject(bank);
            System.out.println("Banking system data has been saved successfully.");
        } catch (IOException e) {
            System.err.println("Error saving bank data: " + e.getMessage());
        }
    }

    public static Bank loadBank() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILENAME))) {
            return (Bank) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("No existing bank data found or error loading. Creating new bank.");
            return new Bank();
        }
    }
}
