package ar.edu.nuevohogar.interfaz.componentes;

import ar.edu.nuevohogar.modelo.Registro;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/** Formularios con etiquetas accesibles y extracción consistente de valores. */
public final class Formulario extends GridPane {
    private final Map<String, Control> controls = new LinkedHashMap<>();
    private int index;
    private final int columns;

    public Formulario() {
        this(2);
    }

    public Formulario(int columns) {
        this.columns = columns;
        setHgap(18);
        setVgap(15);
        getStyleClass().add("form");
        for (int i = 0; i < columns; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / columns);
            c.setHgrow(Priority.ALWAYS);
            getColumnConstraints().add(c);
        }
    }

    private <T extends Control> T field(String key, String title, T input) {
        Label l = new Label(title);
        l.setLabelFor(input);
        input.setAccessibleText(title);
        input.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(6, l, input);
        add(box, index % columns, index / columns);
        index++;
        controls.put(key, input);
        return input;
    }

    public TextField text(String key, String title, String value) {
        return field(key, title, new TextField(value == null ? "" : value));
    }

    public PasswordField password(String key, String title) {
        return field(key, title, new PasswordField());
    }

    public TextArea area(String key, String title, String value) {
        TextArea a = new TextArea(value);
        a.setWrapText(true);
        a.setPrefRowCount(3);
        return field(key, title, a);
    }

    public ComboBox<String> choice(String key, String title, String value, String... options) {
        ComboBox<String> c = new ComboBox<>();
        c.getItems().addAll(options);
        c.setValue(value == null || value.isBlank() ? options[0] : value);
        return field(key, title, c);
    }

    public ComboBox<Registro> rows(String key, String title, List<Registro> rows, int selected, boolean nullable) {
        ComboBox<Registro> c = new ComboBox<>();
        if (nullable) {
            Registro none = new Registro();
            none.put("id", 0);
            none.put("etiqueta", "Sin especificar");
            c.getItems().add(none);
        }
        c.getItems().addAll(rows);
        c.getItems().stream()
                .filter(r -> r.id("id") == selected)
                .findFirst()
                .ifPresent(c::setValue);
        if (c.getValue() == null && !c.getItems().isEmpty()) c.getSelectionModel().selectFirst();
        return field(key, title, c);
    }

    public CheckBox check(String key, String title, boolean value) {
        CheckBox b = new CheckBox(title);
        b.setSelected(value);
        return field(key, title, b);
    }

    public DatePicker date(String key, String title, String value) {
        DatePicker p = new DatePicker();
        if (value != null && !value.isBlank())
            p.setValue(java.time.LocalDate.parse(value.substring(0, 10)));
        p.setEditable(false);
        return field(key, title, p);
    }

    public Map<String, String> values() {
        Map<String, String> v = new HashMap<>();
        controls.forEach(
                (k, c) -> {
                    String value = "";
                    if (c instanceof TextInputControl t) value = t.getText().trim();
                    else if (c instanceof ComboBox<?> b) {
                        Object o = b.getValue();
                        value =
                                o instanceof Registro r
                                        ? (r.id("id") == 0 ? "" : "" + r.id("id"))
                                        : Objects.toString(o, "");
                    } else if (c instanceof CheckBox b) value = b.isSelected() ? "1" : "0";
                    else if (c instanceof DatePicker p) value = Objects.toString(p.getValue(), "");
                    v.put(k, value);
                });
        return v;
    }

    public String value(String key) {
        return values().getOrDefault(key, "");
    }

    public Control control(String key) {
        return controls.get(key);
    }
}
