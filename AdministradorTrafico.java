// AdministradorTrafico.java

import java.io.*;  // Para manejo de entradas/salidas
import java.net.*; // Para manejo de sockets y direcciones de red

// Clase principal que actúa como un proxy simple para redirigir tráfico a dos servidores
class AdministradorTrafico {
    // Variables para almacenar IPs y puertos de los servidores y el puerto local donde escuchará el proxy
    static String servidor1;
    static int puertoServidor1;
    static String servidor2;
    static int puertoServidor2;
    static int puertoLocal;

    // Clase interna que maneja cada conexión entrante como un hilo separado
    static class Worker extends Thread {
        Socket cliente; // Socket que representa la conexión con el cliente

        // Constructor que recibe el socket del cliente
        Worker(Socket cliente) {
            this.cliente = cliente;
        }

        public void run() {
            try {
                // Establece conexión con los dos servidores de destino
                Socket servidor1Socket = new Socket(servidor1, puertoServidor1);
                Socket servidor2Socket = new Socket(servidor2, puertoServidor2);

                // Configura los flujos de entrada/salida para cliente y servidores
                BufferedReader entradaCliente = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
                PrintWriter salidaServidor1 = new PrintWriter(servidor1Socket.getOutputStream(), true);
                PrintWriter salidaServidor2 = new PrintWriter(servidor2Socket.getOutputStream(), true);
                InputStream entradaServidor1Binaria = servidor1Socket.getInputStream();
                InputStream entradaServidor2Binaria = servidor2Socket.getInputStream();
                OutputStream salidaCliente = cliente.getOutputStream();

                // Variables auxiliares para construir la petición HTTP
                String linea;
                StringBuilder peticion = new StringBuilder();
                boolean primerLinea = true;
                String ifModifiedSince = null;

                // Lectura de la petición HTTP del cliente
                while ((linea = entradaCliente.readLine()) != null && !linea.isEmpty()) {
                    if (primerLinea) {
                        // Imprime la primera línea de la petición (ej. GET /index.html HTTP/1.1)
                        System.out.println("Petición recibida en el proxy: " + linea);
                        primerLinea = false;
                    }
                    // Captura cabecera "If-Modified-Since" si existe
                    if (linea.startsWith("If-Modified-Since: ")) {
                        ifModifiedSince = linea.substring(19);
                    }
                    // Agrega la línea a la petición completa
                    peticion.append(linea).append("\r\n");
                }
                peticion.append("\r\n"); // Marca el final de la petición HTTP

                // Envía la misma petición a ambos servidores
                salidaServidor1.print(peticion.toString());
                salidaServidor1.flush();
                salidaServidor2.print(peticion.toString());
                salidaServidor2.flush();

                // Lectura de la respuesta del servidor 1
                ByteArrayOutputStream respuestaServidor1 = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; // Buffer para lectura binaria
                int bytesRead;
                while ((bytesRead = entradaServidor1Binaria.read(buffer)) != -1) {
                    respuestaServidor1.write(buffer, 0, bytesRead); // Guarda respuesta
                }

                // Lectura de la respuesta del servidor 2, pero no se utiliza
                while ((bytesRead = entradaServidor2Binaria.read(buffer)) != -1) {
                    // Se lee, pero no se procesa ni reenvía
                }
                System.out.println("Respuesta del Servidor-2 recibida, pero no enviada al cliente.");

                // Envía la respuesta del servidor 1 al cliente
                salidaCliente.write(respuestaServidor1.toByteArray());
                salidaCliente.flush();

            } catch (IOException e) {
                // Manejo de errores de conexión
                System.err.println("Error en la conexión: " + e.getMessage());
            } finally {
                // Cierra la conexión con el cliente si no es nula
                try {
                    if (cliente != null) cliente.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
    }

    // Método principal del programa
    public static void main(String[] args) throws Exception {
        // Verifica que se pasen exactamente 5 argumentos
        if (args.length != 5) {
            System.err.println("Uso: java AdministradorTrafico <puerto-local> <IP-Servidor-1> <puerto-Servidor-1> <IP-Servidor-2> <puerto-Servidor-2>");
            System.exit(1); // Termina el programa con error
        }

        // Asigna los parámetros pasados por consola a las variables globales
        puertoLocal = Integer.parseInt(args[0]);
        servidor1 = args[1];
        puertoServidor1 = Integer.parseInt(args[2]);
        servidor2 = args[3];
        puertoServidor2 = Integer.parseInt(args[4]);

        // Mensajes informativos de inicio
        System.out.println("Proxy escuchando en puerto: " + puertoLocal);
        System.out.println("Redirigiendo tráfico entre " + servidor1 + ":" + puertoServidor1 + " y " + servidor2 + ":" + puertoServidor2);

        // Crea el socket del servidor que escucha en el puerto local
        ServerSocket serverSocket = new ServerSocket(puertoLocal);

        // Bucle infinito que acepta conexiones de clientes
        while (true) {
            Socket cliente = serverSocket.accept(); // Espera conexión de cliente
            new Worker(cliente).start(); // Crea un hilo para manejar la conexión
        }
    }
}