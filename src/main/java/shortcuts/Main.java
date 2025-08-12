package shortcuts;

public class Main {

    public static void main(String[] args) {
        var namespace = "/" + Main.class.getPackage().getName().replace(".", "/");
        var resourcePath = "/actions.xml";
        var url = Main.class.getResource(resourcePath);

        if (url != null) {
            XActionListener listener =
                    (xaction, oldShortcut) ->
                            System.out.println("heard " + xaction + " formerly " + oldShortcut);
            new ShortcutManager(url, namespace, listener).setVisible(true);
        } else {
            System.err.println("no keyboard shortcuts file found");
        }
    }
}
