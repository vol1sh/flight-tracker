package com.tracker.flight.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class EmailSenderServiceTest {
    @Test void sendsThroughAnActualLocalSmtpConnection() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            Future<String> received = executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    var output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    output.print("220 localhost test SMTP\r\n"); output.flush();
                    boolean data = false;
                    StringBuilder message = new StringBuilder();
                    for (String line; (line = input.readLine()) != null;) {
                        if (data) {
                            if (line.equals(".")) { data = false; output.print("250 queued\r\n"); }
                            else message.append(line).append('\n');
                        } else if (line.startsWith("DATA")) {
                            data = true; output.print("354 End with dot\r\n");
                        } else if (line.startsWith("QUIT")) {
                            output.print("221 bye\r\n"); output.flush(); break;
                        } else output.print("250 OK\r\n");
                        output.flush();
                    }
                    return message.toString();
                }
            });
            var smtp = new JavaMailSenderImpl();
            smtp.setHost(server.getInetAddress().getHostAddress());
            smtp.setPort(server.getLocalPort());
            smtp.getJavaMailProperties().put("mail.smtp.connectiontimeout", "5000");
            smtp.getJavaMailProperties().put("mail.smtp.timeout", "5000");
            new EmailSenderService(smtp, "no-reply@flight-tracker.test")
                    .send("subscriber@example.com", "SU100 changed", "Arrival delay 30");
            assertThat(received.get(5, TimeUnit.SECONDS)).contains("subscriber@example.com",
                    "no-reply@flight-tracker.test", "SU100 changed", "Arrival delay 30");
        }
    }
}
