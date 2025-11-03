import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Cliente de chat que se conecta a un ServidorChat.
 * Permite al usuario enviar comandos y mensajes, mientras un hilo
 * concurrente escucha las respuestas y mensajes del servidor.
 */
public class ClienteChat {

    // El puerto debe coincidir con el del servidor
    private static final int PUERTO = 8080; 

    private static Socket socket;
    private static BufferedReader entrada;
    private static PrintWriter salida;
    private static Thread hiloLector;
    private static final Scanner teclado = new Scanner(System.in);

    /**
     * Punto de entrada principal del cliente.
     * Mantiene un bucle que lee la entrada del usuario y la
     * delega a los métodos correspondientes.
     */
    public static void main(String[] args) {
        System.out.println("Cliente de chat");
        mostrarAyuda();

        try {
            while (true) {
                System.out.print("> ");
                String linea = teclado.nextLine().trim();
                if (linea.isEmpty()) continue;

                try {
                    // --- Procesamiento de comandos locales ---
                    if (linea.startsWith("start-conection")) {
                        String[] partes = linea.split("\\s+", 2);
                        if (partes.length < 2) {
                            System.out.println("Uso: start-conection <IP>");
                            continue;
                        }
                        iniciarConexion(partes[1]);

                    } else if (linea.equalsIgnoreCase("salir")) {
                        if (estaConectado()) salida.println("salir");
                        cerrarSilencioso();
                        System.out.println("Sesión finalizada.");
                        break;
                    
                    } else if (linea.equalsIgnoreCase("help")) {
                        mostrarAyuda();

                    // --- Comandos que se envían al servidor ---
                    } else if (linea.startsWith("change-userName")) {
                        enviarComandoServidor(linea);

                    } else if (linea.startsWith("send-msg")) {
                        enviarComandoServidor(linea);

                    } else if (linea.startsWith("global-msg")) {
                        enviarComandoServidor(linea);

                    } else {
                        // Atajo: cualquier otro texto se envía como mensaje global
                        enviarComandoServidor("global-msg " + linea);
                    }
                    
                } catch (IOException e) {
                    System.out.println("Error de conexión: " + e.getMessage());
                    cerrarSilencioso();
                } catch (IllegalStateException e) {
                    // Captura la excepción de 'requerirConexion'
                    System.out.println("Error: " + e.getMessage());
                }
            }
        } finally {
            cerrarSilencioso();
        }
    }

    /**
     * Establece la conexión con el servidor usando la IP y el puerto definidos.
     * Inicializa los flujos de E/S y, fundamentalmente, inicia el
     * hilo 'hiloLector' para recibir mensajes de forma asíncrona.
     *
     * @throws IOException Si no se puede establecer la conexión.
     */
    private static void iniciarConexion(String ip) throws IOException {
        if (estaConectado()) {
            System.out.println("Ya estás conectado.");
            return;
        }
        
        System.out.println("Conectando a " + ip + ":" + PUERTO + "...");
        socket = new Socket(ip, PUERTO);
        
        entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        salida = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);

        // Hilo concurrente para escuchar al servidor
        hiloLector = new Thread(() -> {
            try {
                String recibido;
                while ((recibido = entrada.readLine()) != null) {
                    // Imprime el mensaje del servidor y vuelve a mostrar el prompt
                    System.out.println("\n< " + recibido); 
                    System.out.print("> ");
                }
            } catch (IOException e) {
                // Ocurre si el socket se cierra (desconexión)
                if (!socket.isClosed()) {
                    System.out.println("\nConexión perdida.");
                }
            }
        });
        hiloLector.setDaemon(true); // Permite que la JVM termine aunque el hilo esté activo
        hiloLector.start();
    }

    /**
     * Envía una línea de texto al servidor.
     * Primero comprueba si la conexión está activa y realiza una
     * validación básica del formato del comando antes de enviarlo.
     */
    private static void enviarComandoServidor(String linea) {
        requerirConexion(); // Lanza error si no está conectado
        
        String[] partes = linea.split("\\s+");
        
        // Validación simple en el cliente
        if (partes[0].equals("change-userName") && partes.length < 2) {
             System.out.println("Uso: change-userName <nuevoNombre>");
        } else if (partes[0].equals("send-msg") && partes.length < 3) {
             System.out.println("Uso: send-msg <usuarioDestino> <mensaje>");
        } else if (partes[0].equals("global-msg") && partes.length < 2) {
             System.out.println("Uso: global-msg <mensaje>");
        } else {
             salida.println(linea); // Envía el comando al servidor
        }
    }

    // ===== Utilidades del Cliente =====

    /**
     * Verifica si el socket está inicializado, conectado y no cerrado.
     */
    private static boolean estaConectado() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Método de guarda que lanza una IllegalStateException si se
     * intenta ejecutar una acción que requiere conexión sin estar conectado.
     */
    private static void requerirConexion() {
        if (!estaConectado()) {
            throw new IllegalStateException("No estás conectado. Usa: start-conection <IP>");
        }
    }

    /**
     * Cierra todos los recursos (Socket, BufferedReader, PrintWriter)
     * de forma segura, ignorando cualquier excepción que ocurra durante el cierre.
     */
    private static void cerrarSilencioso() {
        try {
            if (socket != null) socket.close();
            if (entrada != null) entrada.close();
            if (salida != null) salida.close();
        } catch (IOException ignored) {
            // Se ignora intencionalmente
        }
    }

    /**
     * Imprime en consola la lista de comandos disponibles para el usuario.
     */
    private static void mostrarAyuda() {
        System.out.println("Comandos disponibles:");
        System.out.println("  start-conection <IP>              # Conecta al servidor (puerto 8080)");
        System.out.println("  change-userName <nuevoNombre>     # Cambia tu nombre");
        System.out.println("  send-msg <usuarioDestino> <msg>   # Envía un mensaje privado");
        System.out.println("  global-msg <mensaje>              # Envía un mensaje global");
        System.out.println("  <cualquier otro texto>            # Atajo para 'global-msg'");
        System.out.println("  help                              # Muestra esta ayuda");
        System.out.println("  salir                             # Cierra la sesión");
    }
}