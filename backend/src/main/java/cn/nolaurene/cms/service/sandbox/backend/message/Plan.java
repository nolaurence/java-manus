package cn.nolaurene.cms.service.sandbox.backend.message;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@JacksonXmlRootElement(localName = "plan")
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    private String message;
    private String goal;
    private String title;

    private List<Step> steps;

    @Override
    public String toString() {
        return "Plan{" +
                "message='" + message + '\'' +
                ", goal='" + goal + '\'' +
                ", title='" + title + '\'' +
                ", steps=" + steps +
                '}';
    }
}
