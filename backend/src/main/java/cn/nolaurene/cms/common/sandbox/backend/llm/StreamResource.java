package cn.nolaurene.cms.common.sandbox.backend.llm;

import lombok.Data;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.InputStream;

@Data
public class StreamResource {

    private CloseableHttpResponse response;

    private InputStream inputStream;
}
