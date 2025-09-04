package cn.nolaurene.cms.service.sandbox.worker;

import jnr.unixsocket.UnixSocket;
import jnr.unixsocket.UnixSocketAddress;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.*;

import jnr.unixsocket.UnixSocketChannel;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description:
 */
public class UnixStreamHTTPConnection {

    private final String socketPath;

    public UnixStreamHTTPConnection(String socketPath) {
        this .socketPath = socketPath;
    }

    public Document call(String methodName) throws Exception {
        UnixSocketAddress address = new UnixSocketAddress(socketPath);
        OutputStream out;
        InputStream in;
        try (UnixSocket socket = UnixSocketChannel.open(address).socket()) {
            out = socket.getOutputStream();
            in = socket.getInputStream();

            String xml = "<?xml version=\"1.0\"?>\n" +
                    "<methodCall>\n" +
                    "  <methodName>" + methodName + "</methodName>\n" +
                    "  <params></params>\n" +
                    "</methodCall>";

            String headers = "POST /RPC2 HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Content-Type: text/xml\r\n" +
                    "Content-Length: " + xml.length() + "\r\n" +
                    "Accept: */*\r\n" +
                    "\r\n";

            out.write(headers.getBytes(StandardCharsets.UTF_8));
            out.write(xml.getBytes(StandardCharsets.UTF_8));
            out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder responseBody = new StringBuilder();
            boolean headersDone = false;

            String line;
            while ((line = reader.readLine()) != null) {
                if (headersDone) {
                    responseBody.append(line);
                } else if (line.isEmpty()) {
                    headersDone = true;
                }
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(responseBody.toString())));
        }
    }
}
