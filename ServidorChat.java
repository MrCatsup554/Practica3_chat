import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor principal para el chat concurrente.
 * Escucha conexiones de clientes en un puerto específico y lanza un hilo
 * (SesionCliente) para cada cliente conectado.
 */
public class ServidorChat {

    private static final int PUERTO = 8080;

    // Almacena todas las sesiones de clientes activas, mapeadas por su nombre de usuario.
    private static final ConcurrentHashMap<String, SesionCliente> sesiones = new ConcurrentHashMap<>();
    
    // Generador atómico para nombres de usuario temporales.
    private static final AtomicInteger contadorUsuarios = new AtomicInteger(1);

    /**
     * Método principal del servidor.
     * Acepta conexiones de clientes en un bucle infinito.
     * (Los argumentos de línea de comandos no se utilizan).
     */
    public static void main(String[] args) {
        System.out.println("Servidor escuchando en el puerto " + PUERTO);
        try (ServerSocket servidor = new ServerSocket(PUERTO)) {
            while (true) {
                Socket socket = servidor.accept();
                String nombreInicial = "usuario" + contadorUsuarios.getAndIncrement();
                SesionCliente sesion = new SesionCliente(socket, nombreInicial);
                new Thread(sesion).start();
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }

    /**
     * Clase interna que maneja la sesión de un cliente individual en un hilo separado.
     * Procesa los comandos entrantes y se comunica con otros clientes.
     */
    static class SesionCliente implements Runnable {
        private final Socket socket;
        private final BufferedReader entrada;
        private final PrintWriter salida;
        
        /**
         * 'volatile' asegura que los cambios a 'nombre' sean visibles
         * instantáneamente para todos los hilos.
         */
        private volatile String nombre; 

        /**
         * Construye un manejador de sesión para un cliente, asociándolo
         * al socket y nombre inicial provistos.
         *
         * @throws IOException Si hay un error al obtener los flujos de E/S.
         */
        SesionCliente(Socket socket, String nombreInicial) throws IOException {
            this.socket = socket;
            this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.salida = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
            this.nombre = nombreInicial;
        }

        /**
         * Bucle principal de escucha del cliente.
         * Lee líneas del cliente y las pasa a procesarComando().
         * Se encarga del registro y la limpieza de la sesión.
         */
        @Override
        public void run() {
            try {
                // Registrar al cliente en el mapa de sesiones
                sesiones.put(nombre, this);
                System.out.println("Cliente conectado: " + nombre + " desde " + socket.getInetAddress());
                
                enviarLinea("Sistema: Bienvenido. Tu usuario temporal es '" + nombre + "'.");
                difundirExcepto("Sistema: Se ha unido al chat: " + nombre, this);

                String linea;
                while ((linea = entrada.readLine()) != null) {
                    if ("salir".equalsIgnoreCase(linea)) break;
                    procesarComando(linea);
                }
            } catch (IOException ignored) {
                // El cliente se desconectó abruptamente (ej. cerró la ventana)
            } finally {
                // Bloque de limpieza: asegurar que el cliente sea eliminado
                sesiones.remove(nombre);
                difundirExcepto("Sistema: Ha salido del chat: " + nombre, this);
                try { 
                    socket.close(); 
                } catch (IOException ignored) {}
                System.out.println("Cliente desconectado: " + nombre + ". Sesiones activas: " + sesiones.size());
            }
        }

        /**
         * Parsea y ejecuta un comando recibido del cliente,
         * basado en la línea de texto completa.
         */
        private void procesarComando(String linea) {
            if (linea.startsWith("change-userName")) {
                String[] partes = linea.split("\\s+", 2);
                if (partes.length < 2) {
                    enviarLinea("Sistema: Uso: change-userName <nuevoNombre>");
                    return;
                }
                String nuevo = partes[1].trim();
                
                // Validar nombre
                if (nuevo.isEmpty() || nuevo.contains(" ") || nuevo.equalsIgnoreCase("Sistema")) {
                    enviarLinea("Sistema: Nombre inválido. Evita espacios o 'Sistema'.");
                    return;
                }
                
                // Intenta registrar el nuevo nombre atómicamente
                if (sesiones.putIfAbsent(nuevo, this) != null) {
                     enviarLinea("Sistema: El nombre '" + nuevo + "' ya está en uso.");
                     return;
                }
                
                // Si tuvo éxito, removemos el antiguo
                sesiones.remove(nombre); 
                String anterior = nombre;
                nombre = nuevo;
                
                enviarLinea("Sistema: Tu nombre ahora es: " + nombre);
                difundirExcepto("Sistema: El usuario " + anterior + " ahora se llama " + nombre, this);
                System.out.println("Cambio de nombre: " + anterior + " -> " + nombre);

            } else if (linea.startsWith("send-msg")) {
                String[] partes = linea.split("\\s+", 3);
                if (partes.length < 3) {
                    enviarLinea("Sistema: Uso: send-msg <usuarioDestino> <mensaje>");
                    return;
                }
                String destino = partes[1].trim();
                String cuerpo = partes[2];

                SesionCliente sesionDestino = sesiones.get(destino);
                
                // Notificar si el destinatario no existe
                if (sesionDestino == null) {
                    enviarLinea("Sistema: Usuario no encontrado: " + destino);
                    return;
                }
                
                if (sesionDestino == this) {
                    enviarLinea("Sistema: No puedes enviarte mensajes privados a ti mismo.");
                    return;
                }
                
                sesionDestino.enviarLinea("[privado de " + nombre + "]: " + cuerpo);
                enviarLinea("Sistema: [enviado a " + destino + "]: " + cuerpo);
                System.out.println("Privado " + nombre + " -> " + destino);

            } else if (linea.startsWith("global-msg")) {
                String[] partes = linea.split("\\s+", 2);
                if (partes.length < 2) {
                    enviarLinea("Sistema: Uso: global-msg <mensaje>");
                    return;
                }
                String cuerpo = partes[1];
                
                // Difunde el mensaje y obtiene el número de receptores
                int contador = difundirExcepto("[" + nombre + "]: " + cuerpo, this);
                
                // Notifica al remitente el conteo de receptores
                enviarLinea("Sistema: Mensaje global enviado a " + contador + " usuario(s).");
                System.out.println("Global " + nombre + " (" + contador + " receptores)");

            } else {
                enviarLinea("Sistema: Comando no reconocido. Usa: change-userName, send-msg, global-msg, salir");
            }
        }

        /**
         * Envía un único mensaje de texto a este cliente.
         * Este método está sincronizado para prevenir escrituras entrelazadas
         * si múltiples hilos intentaran escribir a este cliente.
         */
        private void enviarLinea(String texto) {
            synchronized (salida) {
                salida.println(texto);
            }
        }
    }

    // ======= Utilidades del Servidor =======
    
    /**
     * Envía un mensaje a todos los clientes conectados, excepto al remitente.
     * Retorna el número de clientes que recibieron el mensaje.
     */
    private static int difundirExcepto(String texto, SesionCliente excepto) {
        int contador = 0;
        // Iterar sobre los valores de un ConcurrentHashMap es seguro para hilos.
        for (SesionCliente sesion : sesiones.values()) {
            if (sesion != excepto) {
                sesion.enviarLinea(texto);
                contador++;
            }
        }
        return contador; // Retorna el número de receptores
    }
}