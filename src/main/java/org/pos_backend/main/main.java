package org.pos_backend.main;

import org.pos_backend.controller.TerminalController;

public class main {
    private static final TerminalController terminalController = TerminalController.getInstance();
    public static void main(String[] args) {
        terminalController.menuOptions();
    }
}