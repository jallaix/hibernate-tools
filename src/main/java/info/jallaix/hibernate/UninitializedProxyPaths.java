package info.jallaix.hibernate;

import lombok.Data;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean holding a fully un-proxied entity and a tree of uninitialized proxy paths.
 */
@Data
public class UninitializedProxyPaths<T> {

    private T entity;
    private List<String> uninitializedProxyPaths = new ArrayList<>();
    //private Node uninitializedProxiesTree;

    public UninitializedProxyPaths(T entity) {
        this.entity = entity;
    }

    @Data
    public static class Node {

        private Field field;
        private Node parent;
        private List<Node> children = new ArrayList<>();

        public Node(Field field) {
            this.field = field;
        }

        public void addChild(Node node) {
            children.add(node);
            node.parent = this;
        }
    }
}
