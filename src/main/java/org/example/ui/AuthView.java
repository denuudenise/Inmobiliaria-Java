package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.dao.UsuarioDao;

import java.sql.SQLException;
import java.util.function.Consumer;

public class AuthView {
    private final UsuarioDao usuarioDao;
    private final Consumer<UsuarioDao.Usuario> onLogin;
    private final Runnable showLogin;
    private final Runnable showRegistration;
    private final boolean registration;

    public AuthView(UsuarioDao usuarioDao, Consumer<UsuarioDao.Usuario> onLogin,
                    Runnable showLogin, Runnable showRegistration, boolean registration) {
        this.usuarioDao = usuarioDao;
        this.onLogin = onLogin;
        this.showLogin = showLogin;
        this.showRegistration = showRegistration;
        this.registration = registration;
    }

    public Parent createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("auth-page");
        root.setLeft(createIntroPanel());
        root.setCenter(registration ? createRegistrationForm() : createLoginForm());
        return root;
    }

    private VBox createIntroPanel() {
        VBox panel = new VBox(22);
        panel.getStyleClass().add("auth-intro");
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setPadding(new Insets(52));
        panel.setPrefWidth(420);
        Label brand = new Label("⌂  Inmobiliaria\n    Nuevo Hogar");
        brand.getStyleClass().add("brand-mark");
        Label title = new Label(registration ? "Encontrá tu próximo hogar." : "Bienvenido/a\na Nuevo Hogar.");
        title.getStyleClass().add("intro-title");
        title.setWrapText(true);
        Label subtitle = new Label("Propiedades, asesoramiento y oportunidades en un solo lugar.");
        subtitle.getStyleClass().add("intro-copy");
        subtitle.setWrapText(true);
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label promise = new Label("Un buen hogar empieza con una buena búsqueda.");
        promise.getStyleClass().add("intro-footnote");
        panel.getChildren().addAll(brand, spacer, title, subtitle, spacer(), promise);
        return panel;
    }

    private Region spacer() { Region r = new Region(); VBox.setVgrow(r, Priority.ALWAYS); return r; }

    private VBox createLoginForm() {
        VBox form = formColumn();
        Label eyebrow = new Label("TU ESPACIO INMOBILIARIO");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Iniciar sesión");
        title.getStyleClass().add("form-title");
        Label help = new Label("Ingresá con el correo y la contraseña de tu cuenta.");
        help.getStyleClass().add("muted");
        TextField email = new TextField();
        email.setPromptText("Correo electrónico");
        email.setAccessibleText("Correo electrónico");
        PasswordField password = new PasswordField();
        password.setPromptText("Contraseña");
        password.setAccessibleText("Contraseña");
        Label message = messageLabel();
        Button submit = primaryButton("Iniciar sesión");
        submit.setDefaultButton(true);
        submit.setOnAction(e -> {
            message.setText("");
            if (email.getText().isBlank() || password.getText().isBlank()) {
                showMessage(message, "Completá tu correo y contraseña.", false); return;
            }
            submit.setDisable(true);
            try {
                UsuarioDao.Usuario usuario = usuarioDao.iniciarSesion(email.getText(), password.getText());
                if (usuario == null) showMessage(message, "Correo o contraseña incorrectos, o cuenta inactiva.", false);
                else onLogin.accept(usuario);
            } catch (SQLException ex) {
                showMessage(message, connectionError(ex), false);
            } finally { submit.setDisable(false); }
        });
        Hyperlink register = new Hyperlink("Crear una cuenta");
        register.setOnAction(e -> showRegistration.run());
        HBox footer = new HBox(4, new Label("¿Todavía no tenés cuenta?"), register);
        footer.setAlignment(Pos.CENTER);
        form.getChildren().addAll(eyebrow, title, help, gap(22), fieldLabel("Correo electrónico"), email,
                gap(8), fieldLabel("Contraseña"), password, message, gap(4), submit, gap(12), footer);
        return form;
    }

    private VBox createRegistrationForm() {
        VBox form = formColumn();
        Label eyebrow = new Label("REGISTRO DE CLIENTE");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Crear cuenta");
        title.getStyleClass().add("form-title");
        Label help = new Label("Completá tus datos para guardar propiedades y contactar agentes.");
        help.getStyleClass().add("muted");
        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        ColumnConstraints col = new ColumnConstraints(); col.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints(); col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col, col2);
        TextField nombre = input("Nombre");
        TextField apellido = input("Apellido");
        TextField dni = input("DNI");
        TextField telefono = input("Teléfono (opcional)");
        TextField email = input("Correo electrónico");
        PasswordField password = new PasswordField(); password.setPromptText("Contraseña (8 caracteres mínimo)");
        PasswordField confirm = new PasswordField(); confirm.setPromptText("Repetir contraseña");
        grid.add(labeled("Nombre", nombre), 0, 0); grid.add(labeled("Apellido", apellido), 1, 0);
        grid.add(labeled("DNI", dni), 0, 1); grid.add(labeled("Teléfono", telefono), 1, 1);
        grid.add(labeled("Correo electrónico", email), 0, 2, 2, 1);
        grid.add(labeled("Contraseña", password), 0, 3, 2, 1);
        grid.add(labeled("Confirmar contraseña", confirm), 0, 4, 2, 1);
        Label message = messageLabel();
        Button submit = primaryButton("Crear mi cuenta");
        submit.setOnAction(e -> {
            message.setText("");
            if (nombre.getText().isBlank() || apellido.getText().isBlank() || dni.getText().isBlank()
                    || email.getText().isBlank() || password.getText().isBlank()) {
                showMessage(message, "Completá todos los campos obligatorios.", false); return;
            }
            if (!email.getText().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                showMessage(message, "Revisá el formato del correo electrónico.", false); return;
            }
            if (password.getText().length() < 8) {
                showMessage(message, "La contraseña debe tener al menos 8 caracteres.", false); return;
            }
            if (password.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                showMessage(message, "La contraseña no puede superar los 72 bytes.", false); return;
            }
            if (!password.getText().equals(confirm.getText())) {
                showMessage(message, "Las contraseñas no coinciden.", false); return;
            }
            submit.setDisable(true);
            try {
                usuarioDao.registrarCliente(nombre.getText(), apellido.getText(), dni.getText(),
                        telefono.getText(), email.getText(), password.getText());
                Alert success = new Alert(Alert.AlertType.INFORMATION,
                        "Tu cuenta de cliente se creó correctamente. Ya podés iniciar sesión.", ButtonType.OK);
                success.setHeaderText("Registro completado");
                success.showAndWait();
                submit.setDisable(false);
                showLogin.run();
            } catch (SQLException ex) {
                String detail = ex.getErrorCode() == 1062
                        ? "Ya existe una cuenta con ese correo o DNI."
                        : connectionError(ex);
                showMessage(message, detail, false);
                submit.setDisable(false);
            }
        });
        Hyperlink login = new Hyperlink("Iniciar sesión");
        login.setOnAction(e -> showLogin.run());
        HBox footer = new HBox(4, new Label("¿Ya tenés cuenta?"), login);
        footer.setAlignment(Pos.CENTER);
        form.getChildren().addAll(eyebrow, title, help, gap(14), grid, message, submit, footer);
        return form;
    }

    private VBox labeled(String label, Control input) {
        VBox box = new VBox(5, fieldLabel(label), input);
        return box;
    }

    private TextField input(String prompt) { TextField f = new TextField(); f.setPromptText(prompt); return f; }
    private Label fieldLabel(String value) { Label l = new Label(value); l.getStyleClass().add("field-label"); return l; }
    private VBox formColumn() {
        VBox box = new VBox(10);
        box.getStyleClass().add("auth-form");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(44, 64, 44, 64));
        box.setMaxWidth(680);
        return box;
    }
    private Label messageLabel() { Label l = new Label(); l.setWrapText(true); l.getStyleClass().add("form-message"); return l; }
    private void showMessage(Label label, String value, boolean success) {
        label.setText(value);
        label.getStyleClass().removeAll("message-error", "message-success");
        label.getStyleClass().add(success ? "message-success" : "message-error");
    }
    private Button primaryButton(String value) { Button b = new Button(value); b.getStyleClass().add("primary-button"); b.setMaxWidth(Double.MAX_VALUE); return b; }
    private Region gap(double height) { Region r = new Region(); r.setMinHeight(height); r.setPrefHeight(height); return r; }

    private String connectionError(SQLException ex) {
        if (ex.getSQLState() != null && ex.getSQLState().startsWith("08")) {
            return "No se pudo conectar con la base de datos. Verificá que MariaDB/MySQL esté activo y que exista la base inmobiliaria.";
        }
        return "No se pudo completar la operación. Revisá la configuración de la base de datos.";
    }
}
