# Practica3_chat en Java

**Nombre:** Wilbert Novelo Ruiz <br>
**Materia:** Concurrencia y Paralelismo <br>
**Semestre:** ITS 5A

---

## Descripción del Proyecto

Este proyecto es una implementación de un sistema de chat multiusuario cliente-servidor desarrollado en Java. El núcleo del proyecto demuestra conceptos clave de **concurrencia** y **paralelismo** utilizando Sockets de Java para la comunicación en red y un manejo robusto de hilos para gestionar múltiples clientes de forma simultánea.

El servidor es capaz de aceptar múltiples conexiones de clientes, y cada cliente se ejecuta en su propio hilo, permitiendo una comunicación en tiempo real, asíncrona e independiente.

---

## Mejoras y Modificaciones Implementadas

Basado en los requisitos, el sistema de chat incluye las siguientes mejoras funcionales que proporcionan retroalimentación esencial al usuario y mejoran la robustez del sistema:

1.  **Notificación de Cambio de Nombre (`change-userName`)**
    * **Modificación:** Al cambiar el nombre de usuario, el servidor no solo actualiza la sesión del cliente, sino que también **difunde una notificación a todos los demás usuarios** conectados.
    * **Resultado:** Todos los participantes del chat son informados del cambio (Ej: `Sistema: El usuario 'usuario1' ahora es 'Wilbert'`).

2.  **Alerta en Mensajes Privados (`send-msg`)**
    * **Modificación:** Si un usuario intenta enviar un mensaje privado a un destinatario que no existe o no está conectado, el servidor **devuelve un mensaje de error únicamente al remitente**.
    * **Resultado:** El usuario recibe retroalimentación inmediata (Ej: `Sistema: Usuario no encontrado: 'usuario_inexistente'`) sin interrumpir a los demás clientes.

3.  **Conteo en Mensajes Globales (`global-msg`)**
    * **Modificación:** Después de enviar un mensaje global, el servidor **calcula cuántos clientes recibieron el mensaje** (excluyendo al propio remitente) y envía esta información de vuelta.
    * **Resultado:** El remitente recibe una confirmación del alcance de su mensaje (Ej: `Sistema: Mensaje global enviado a 5 usuario(s).`).

---

## Arquitectura Concurrente

La naturaleza concurrente de esta aplicación se gestiona de la siguiente manera:

### Servidor (`ServidorChat.java`)

* **Manejo Multi-hilo:** El `ServerSocket` principal se ejecuta en el hilo principal, esperando conexiones. Por cada `socket` de cliente aceptado, se instancia y ejecuta una nueva clase `SesionCliente` (que implementa `Runnable`) en un **hilo completamente nuevo** (`new Thread(sesion).start()`).
* **Gestión de Sesiones Thread-Safe:** Se utiliza un `ConcurrentHashMap<String, SesionCliente>` para almacenar las sesiones activas. Esta estructura de datos está diseñada específicamente para el acceso concurrente, previniendo condiciones de carrera (`race conditions`) al agregar, eliminar o buscar usuarios de manera segura desde múltiples hilos.

### Cliente (`ClienteChat.java`)

* **Modelo de Doble Hilo:** El cliente utiliza dos hilos para lograr una experiencia de usuario no bloqueante:
    1.  **Hilo Principal:** Se encarga de leer la entrada del usuario desde la consola (`Scanner`).
    2.  **Hilo Lector:** Al conectarse, se lanza un hilo `hiloLector` (demonio) que se dedica exclusivamente a escuchar (`entrada.readLine()`) los mensajes entrantes del servidor.

Esta separación permite que el usuario pueda **escribir comandos al mismo tiempo que recibe mensajes** globales o privados de otros usuarios.

---

## Comandos Disponibles

El cliente opera mediante los siguientes comandos:

| Comando | Descripción |
| :--- | :--- |
| `start-conection <IP>` | Inicia la conexión con el servidor en la IP especificada. |
| `change-userName <nombre>` | Solicita un cambio de nombre de usuario. |
| `send-msg <usuario> <msg>` | Envía un mensaje privado al `<usuario>` especificado. |
| `global-msg <mensaje>` | Envía un mensaje a todos los usuarios conectados. |
| `<cualquier otro texto>` | Funciona como un atajo para `global-msg`. |
| `help` | Muestra la lista de comandos disponibles. |
| `salir` | Desconecta al cliente del servidor. |

---

## Estructura del Código

* `ServidorChat.java`: Contiene la lógica del servidor, el `ServerSocket` y la clase interna `SesionCliente` que maneja cada conexión.
* `ClienteChat.java`: Contiene la lógica del cliente, el manejo de entrada del usuario y el hilo lector para recibir mensajes.
