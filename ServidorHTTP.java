// ServidorHTTP.java

import java.net.ServerSocket;              // Permite crear un servidor que escucha conexiones en un puerto específico
import java.net.Socket;                    // Representa una conexión entre cliente y servidor
import java.util.Date;                     // Clase para trabajar con fechas
import java.text.SimpleDateFormat;         // Clase para formatear fechas en formato legible o compatible con HTTP
import java.io.BufferedReader;             // Permite leer texto desde un flujo de entrada de manera eficiente
import java.io.InputStreamReader;          // Convierte flujos de bytes en caracteres
import java.io.PrintWriter;                // Permite escribir texto en un flujo de salida de manera sencilla

class ServidorHTTP {
    // Valor constante que se usará como cabecera "Last-Modified" en las respuestas HTTP
    static final String LAST_MODIFIED = "Fri, 01 Mar 2024 12:00:00 GMT";

    // Clase interna que representa un hilo de atención para cada cliente que se conecta
    static class Worker extends Thread {
        Socket conexion;

        Worker(Socket conexion) {
            this.conexion = conexion;
        }

        // Función que extrae el valor de una variable (a, b o c) de la URL con formato query (?a=1&b=2)
        int valor(String parametros, String variable) throws Exception {
            String[] p = parametros.split("&");
            for (String parametro : p) {
                String[] s = parametro.split("=");
                if (s[0].equals(variable))
                    return Integer.parseInt(s[1]);
            }
            throw new Exception("Se espera la variable: " + variable);
        }

        public void run() {
            try {
                // Lectores y escritores para comunicarse con el cliente
                BufferedReader entrada = new BufferedReader(new InputStreamReader(conexion.getInputStream()));
                PrintWriter salida = new PrintWriter(conexion.getOutputStream());

                // Leer la primera línea de la petición (ej. GET / HTTP/1.1)
                String req = entrada.readLine();
                if (req == null) {
                    System.err.println("Conexión cerrada por el cliente.");
                    return;
                }
                System.out.println("Petición recibida: " + req);

                // Ignora la petición de favicon.ico (que hacen los navegadores automáticamente)
                if (req.startsWith("GET /favicon.ico")) {
                    salida.println("HTTP/1.1 204 No Content");
                    salida.println("Connection: close");
                    salida.println();
                    salida.flush();
                    return;
                }

                // Leer los encabezados HTTP adicionales
                String encabezado;
                String ifModifiedSince = null;
                while (!(encabezado = entrada.readLine()).equals("")) {
                    System.out.println("Encabezado: " + encabezado);
                    if (encabezado.startsWith("If-Modified-Since: ")) {
                        ifModifiedSince = encabezado.substring(19); // Extraer la fecha
                    }
                }

                // Si la petición es al recurso raíz
                if (req.startsWith("GET / ")) {
                    // Si el cliente ya tiene la versión más reciente, responder con 304
                    if (ifModifiedSince != null && ifModifiedSince.equals(LAST_MODIFIED)) {
                        salida.println("HTTP/1.1 304 Not Modified");
                        salida.println("Connection: close");
                        salida.println();
                    } else {
                        // Enviar HTML con un botón que hace una petición AJAX
                        String respuesta = "<html>"
                                + "<script>"
                                + "function get(req,callback){"
                                + "const xhr = new XMLHttpRequest();"
                                + "xhr.open('GET', req, true);"
                                + "xhr.onload=function(){"
                                + "if (callback != null) callback(xhr.status,xhr.response);"
                                + "};"
                                + "xhr.send();"
                                + "}"
                                + "</script>"
                                + "<body>"
                                + "<button onclick=\"get('/suma?a=1&b=2&c=3',function(status,response){alert(status + ' ' + response);})\">Aceptar</button>"
                                + "</body>"
                                + "</html>";
                        // Encabezados de la respuesta
                        salida.println("HTTP/1.1 200 OK");
                        salida.println("Content-type: text/html; charset=utf-8");
                        salida.println("Content-length: " + respuesta.length());
                        salida.println("Last-Modified: " + LAST_MODIFIED);
                        salida.println("Connection: close");
                        salida.println();
                        salida.println(respuesta); // Cuerpo HTML
                    }
                    salida.flush();

                // Si la petición es al recurso /suma con parámetros a, b y c
                } else if (req.startsWith("GET /suma?")) {
                    // Extraer los parámetros de la URL
                    String parametros = req.split(" ")[1].split("\\?")[1];
                    // Calcular la suma
                    String respuesta = String.valueOf(valor(parametros, "a") + valor(parametros, "b") + valor(parametros, "c"));
                    // Enviar la respuesta como texto plano
                    salida.println("HTTP/1.1 200 OK");
                    salida.println("Access-Control-Allow-Origin: *"); // Permite llamadas desde otras páginas (CORS)
                    salida.println("Content-type: text/plain; charset=utf-8");
                    salida.println("Content-length: " + respuesta.length());
                    salida.println("Connection: close");
                    salida.println();
                    salida.println(respuesta); // Resultado de la suma
                    salida.flush();

                // Si la URL no es reconocida, enviar error 404
                } else {
                    salida.println("HTTP/1.1 404 File Not Found");
                    salida.flush();
                }

            } catch (Exception e) {
                System.err.println("Error en la conexión: " + e.getMessage());
            } finally {
                try {
                    conexion.close(); // Cierra la conexión al terminar
                } catch (Exception e) {
                    System.err.println("Error en close: " + e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Verificar que se haya pasado el puerto como argumento
        if (args.length != 1) {
            System.err.println("Uso: java ServidorHTTP <puerto>");
            System.exit(1);
        }

        int puerto = Integer.parseInt(args[0]);
        System.out.println("Intentando iniciar servidor en puerto: " + puerto);

        // Crear socket del servidor
        ServerSocket servidor = new ServerSocket(puerto);
        System.out.println("Servidor HTTP escuchando en puerto: " + puerto);

        // Bucle infinito: atender conexiones una por una, cada una en un hilo (Worker)
        while (true) {
            System.out.println("Esperando conexión...");
            Socket conexion = servidor.accept();
            System.out.println("Conexión aceptada desde: " + conexion.getInetAddress());

            new Worker(conexion).start(); // Crear y arrancar un nuevo hilo para atender al cliente
        }
    }
}