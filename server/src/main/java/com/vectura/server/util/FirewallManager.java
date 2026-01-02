package com.vectura.server.util;

public class FirewallManager {

    private static final String RULE_NAME = "Vectura SFTP Server";

    public static boolean configureFirewall(int currentPort) {
        String executablePath = getCurrentProcessPath();
        String command;

        // Comando para borrar regla vieja (limpieza)
        String deleteCmd = String.format("netsh advfirewall firewall delete rule name=\"%s\"", RULE_NAME);

        if (isProductionExe(executablePath)) {
            // Abrir el firewall para el programa (puerto dinámico)
            command = deleteCmd + " & " + String.format(
                    "netsh advfirewall firewall add rule name=\"%s\" dir=in action=allow program=\"%s\" enable=yes profile=any",
                    RULE_NAME, executablePath
            );
        } else {
            // Abrir solo el puerto específico
            command = deleteCmd + " & " + String.format(
                    "netsh advfirewall firewall add rule name=\"%s\" dir=in action=allow protocol=TCP localport=%d profile=any",
                    RULE_NAME, currentPort
            );
        }

        return runAsAdmin(command);
    }

    private static String getCurrentProcessPath() {
        return ProcessHandle.current().info().command().orElse("");
    }

    private static boolean isProductionExe(String path) {
        if (path == null || path.isEmpty()) return false;
        String lower = path.toLowerCase();
        return !lower.endsWith("java.exe") && !lower.endsWith("javaw.exe");
    }

    private static boolean runAsAdmin(String cmd) {
        // Usar PowerShell para pedir permisos de Admin
        String psCommand = "Start-Process cmd -ArgumentList '/c " + cmd + "' -Verb RunAs -WindowStyle Hidden";
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", psCommand);
            pb.start();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}