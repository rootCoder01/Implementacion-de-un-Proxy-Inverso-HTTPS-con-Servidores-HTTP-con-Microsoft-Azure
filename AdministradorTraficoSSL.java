// AdministradorTraficoSSL.java

import java.io.*;                           // Manejo de entrada/salida: InputStream, OutputStream, BufferedReader, PrintWriter, etc.
import java.net.*;                          // Manejo de conexiones de red: Socket, ServerSocket, InetAddress, etc.
import javax.net.ssl.*;                     // Clases para conexiones seguras SSL/TLS: SSLSocket, SSLServerSocket, SSLContext, etc.
import java.security.KeyStore;              // Para cargar y gestionar almacenes de claves (keystore) utilizados en SSL/TLS.
import javax.net.ssl.KeyManagerFactory;     // Para gestionar claves privadas y certificados del servidor en conexiones SSL.
import javax.net.ssl.SSLContext;            // Configura el entorno SSL/TLS, incluyendo claves y protocolos usados en la comunicación segura.

// Clase principal que actúa como un proxy inverso con soporte para conexiones seguras mediante SSL
class AdministradorTraficoSSL {
    // Variables estáticas para almacenar configuración del proxy y contexto SSL
    static String servidor1;
    static int puertoServidor1;
    static String servidor2;
    static int puertoServidor2;
    static int puertoLocal;
    static SSLContext sslContext;

    // Clase interna Worker encargada de manejar cada conexión entrante de forma concurrente
    static class Worker extends Thread {
        SSLSocket cliente;

        Worker(SSLSocket cliente) {
            this.cliente = cliente;
        }

        public void run() {
            try {
                // Se establecen conexiones con los dos servidores HTTP definidos
                Socket servidor1Socket = new Socket(servidor1, puertoServidor1);
                Socket servidor2Socket = new Socket(servidor2, puertoServidor2);

                // Se crean los flujos de entrada y salida para la comunicación con el cliente y los servidores
                BufferedReader entradaCliente = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
                PrintWriter salidaServidor1 = new PrintWriter(servidor1Socket.getOutputStream(), true);
                PrintWriter salidaServidor2 = new PrintWriter(servidor2Socket.getOutputStream(), true);
                InputStream entradaServidor1Binaria = servidor1Socket.getInputStream();
                InputStream entradaServidor2Binaria = servidor2Socket.getInputStream();
                OutputStream salidaCliente = cliente.getOutputStream();

                // Se prepara para recibir la petición HTTP del cliente
                String linea;
                StringBuilder peticion = new StringBuilder();
                boolean primerLinea = true;
                String ifModifiedSince = null;

                // Lectura de la petición HTTP línea por línea hasta encontrar una línea vacía (fin de cabeceras)
                while ((linea = entradaCliente.readLine()) != null && !linea.isEmpty()) {
                    if (primerLinea) {
                        System.out.println("Petición recibida en el proxy: " + linea);
                        primerLinea = false;
                    }
                    if (linea.startsWith("If-Modified-Since: ")) {
                        ifModifiedSince = linea.substring(19); // Extrae la fecha si está presente
                    }
                    peticion.append(linea).append("\r\n");
                }
                peticion.append("\r\n"); // Finaliza la petición HTTP

                // Envía la misma petición HTTP a ambos servidores
                salidaServidor1.print(peticion.toString());
                salidaServidor1.flush();
                salidaServidor2.print(peticion.toString());
                salidaServidor2.flush();

                // Lee la respuesta binaria del servidor 1
                ByteArrayOutputStream respuestaServidor1 = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = entradaServidor1Binaria.read(buffer)) != -1) {
                    respuestaServidor1.write(buffer, 0, bytesRead);
                }

                // Lee la respuesta del servidor 2 pero no la reenvía al cliente
                while ((bytesRead = entradaServidor2Binaria.read(buffer)) != -1) {
                    // No se almacena ni se procesa la respuesta del servidor 2
                }
                System.out.println("Respuesta del Servidor-2 recibida, pero no enviada al cliente.");

                // Envia al cliente la respuesta obtenida del servidor 1
                salidaCliente.write(respuestaServidor1.toByteArray());
                salidaCliente.flush();

            } catch (IOException e) {
                System.err.println("Error en la conexión: " + e.getMessage());
            } finally {
                try {
                    if (cliente != null) cliente.close(); // Cierra la conexión con el cliente
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Validación de argumentos de entrada
        if (args.length != 5) {
            System.err.println("Uso: java AdministradorTraficoSSL <puerto-local> <IP-Servidor-1> <puerto-Servidor-1> <IP-Servidor-2> <puerto-Servidor-2>");
            System.exit(1);
        }

        // Asignación de parámetros desde los argumentos
        puertoLocal = Integer.parseInt(args[0]);
        servidor1 = args[1];
        puertoServidor1 = Integer.parseInt(args[2]);
        servidor2 = args[3];
        puertoServidor2 = Integer.parseInt(args[4]);

        // Mensajes informativos sobre el estado del proxy
        System.out.println("Proxy SSL escuchando en puerto: " + puertoLocal);
        System.out.println("Redirigiendo tráfico entre " + servidor1 + ":" + puertoServidor1 + " y " + servidor2 + ":" + puertoServidor2);

        // Configuración del contexto SSL con el keystore que contiene el certificado del servidor
        sslContext = SSLContext.getInstance("TLS");
        KeyStore keyStore = KeyStore.getInstance("PKCS12"); // También puede ser "JKS" dependiendo del formato
        FileInputStream keyFile = new FileInputStream("keystore_servidor.jks"); // Carga el keystore desde el archivo
        keyStore.load(keyFile, "password".toCharArray()); // Desbloquea el keystore con la contraseña

        // Inicializa el KeyManagerFactory con las llaves del keystore
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "password".toCharArray()); // Usa la misma contraseña para las claves

        // Inicializa el contexto SSL con los KeyManagers
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        // Crea el socket SSL del servidor que escucha conexiones entrantes en el puerto especificado
        SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(puertoLocal);

        // Bucle infinito para aceptar y manejar conexiones entrantes de clientes
        while (true) {
            SSLSocket cliente = (SSLSocket) serverSocket.accept(); // Espera conexión entrante
            new Worker(cliente).start(); // Crea un hilo Worker para manejar la conexión de manera concurrente
        }
    }
}