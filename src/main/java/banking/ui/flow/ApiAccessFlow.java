package banking.ui.flow;

import banking.api.BankHttpServer;
import banking.security.AuthService;
import banking.security.AuthToken;
import banking.security.Role;
import banking.service.Bank;
import banking.ui.console.ConsoleIO;

import java.util.Optional;

/**
 * Console utility flow that authenticates an operator before exposing the HTTP API.
 */
public class ApiAccessFlow {
    private final Bank bank;
    private final ConsoleIO io;
    private final AuthService authService;

    private BankHttpServer httpServer;
    private int boundPort;

    public ApiAccessFlow(Bank bank, ConsoleIO io, AuthService authService) {
        this.bank = bank;
        this.io = io;
        this.authService = authService;
    }

    public void manageHttpGateway() {
        io.heading("HTTP API Management");
        if (httpServer != null) {
            io.info("HTTP API is running on port " + boundPort + ".");
            String stop = io.prompt("Stop the server? (yes/no): ");
            if ("yes".equalsIgnoreCase(stop.trim())) {
                shutdown();
                io.success("HTTP API stopped.");
            } else {
                io.info("Server remains active.");
            }
            return;
        }

        io.info("Operator credentials are required to expose the shared HTTP API.");
        String username = io.prompt("Operator username: ");
        String password = io.prompt("Operator password: ");

        Optional<AuthToken> maybeToken = authService.authenticate(username, password);
        if (maybeToken.isEmpty()) {
            io.error("Invalid credentials.");
            return;
        }
        AuthToken token = maybeToken.get();
        if (!authService.hasAnyRole(token.principal(), Role.OPERATOR)) {
            io.error("Only operator accounts can launch the HTTP API.");
            return;
        }

        String portInput = io.prompt("Enter port for HTTP API (press Enter for 8080): ");
        int port = 8080;
        if (portInput != null && !portInput.isBlank()) {
            try {
                port = Integer.parseInt(portInput.trim());
            } catch (NumberFormatException e) {
                io.error("Invalid port value.");
                return;
            }
            if (port <= 0 || port > 65535) {
                io.error("Port must be between 1 and 65535.");
                return;
            }
        }

        httpServer = new BankHttpServer(bank, port, authService);
        try {
            httpServer.start();
            boundPort = httpServer.getPort();
            io.success("HTTP API started on port " + boundPort + ".");
            io.info("Distribute this session token to API clients: " + token.token());
            io.info("Token expires at: " + token.expiresAt());
        } catch (RuntimeException exception) {
            io.error("Failed to start HTTP API: " + exception.getMessage());
            shutdown();
        }
    }

    public void shutdown() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
            boundPort = 0;
        }
    }
}
