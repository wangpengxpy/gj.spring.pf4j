package gj.pf4j.descriptor;

import lombok.Getter;
import lombok.Setter;
import org.pf4j.DefaultPluginDescriptor;

@Setter
@Getter
public class GJPluginDescriptor extends DefaultPluginDescriptor {
    private int order = 100000;
}
