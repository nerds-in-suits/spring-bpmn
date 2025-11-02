package de.nemeses.spring.tasks;

import java.util.LinkedList;
import java.util.List;

public record TestPayload(String name, int amount,  List<TestPayload> subs) {

    static TestPayload generate() {
        return TestPayload.generate(5);
    }

    static TestPayload generate(final int depth) {
        return TestPayload.generate("1", 5);
    }

    private static TestPayload generate(final String name, final int depth) {
        List<TestPayload> children=null;
        if(depth > 1) {
            children=new LinkedList<>();
            for(int i=0;i<depth;i++) {
                children.add(TestPayload.generate(name + '.' + i, depth-1));
            }
        }
        return new TestPayload(name, name.hashCode(), children);
    }
}
