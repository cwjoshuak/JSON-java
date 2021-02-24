package org.json;

public class JSONNode {

    private String key;
    private Object value;
    private Object parent;

    private boolean isJSONArrayElement;
    private int index;

    public JSONNode(String key, Object value, Object parent, int index){
        this.key = key;
        this.value = value;
        this.parent = parent;
        this.isJSONArrayElement = index != -1;
        this.index = index;
    }

    public boolean isJSONArrayElement() {
        return isJSONArrayElement;
    }

    public int getIndex() {
        return index;
    }

    public Object getParent() {
        return parent;
    }

    public Object getValue() {
        return value;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "JSONNode{" +
                "key='" + key + '\'' +
                ", value=" + value +
                ", parent=" + parent +
                ", isJSONArrayElement=" + isJSONArrayElement +
                ", index=" + index +
                '}';
    }
}
