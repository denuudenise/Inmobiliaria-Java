package ar.edu.nuevohogar.interfaz;

import ar.edu.nuevohogar.persistencia.dao.ConexionBaseDatos;
import ar.edu.nuevohogar.persistencia.dao.PanelAdministradorDAO;
import ar.edu.nuevohogar.modelo.DatosPanelAdministrador;
import ar.edu.nuevohogar.modelo.Registro;
import ar.edu.nuevohogar.backend.*;
import ar.edu.nuevohogar.interfaz.paneles.ElementoMenuPanel;
import ar.edu.nuevohogar.interfaz.paneles.administrador.PanelAdministrador;
import ar.edu.nuevohogar.interfaz.paneles.agente.PanelAgente;
import ar.edu.nuevohogar.interfaz.paneles.cliente.PanelCliente;
import ar.edu.nuevohogar.interfaz.componentes.Formulario;

import javafx.application.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.*;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

/** Presentación JavaFX de gestión interna, con espacios de trabajo según el rol. */
public final class AplicacionInmobiliaria extends Application {
    private record ElementoGaleria(String uri, boolean video) {}

    private ServicioInmobiliaria service;
    private Stage stage;
    private final BorderPane root = new BorderPane();
    private VBox content;
    private String activePage = "Iniciar sesión";
    private Runnable currentPageReload;
    private MediaPlayer activeMediaPlayer;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Nuevo Hogar · Gestión inmobiliaria");
        javafx.geometry.Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(
                root,
                Math.min(1320, screen.getWidth()),
                Math.min(860, screen.getHeight()));
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/theme.css")).toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(Math.min(1000, screen.getWidth()));
        stage.setMinHeight(Math.min(620, screen.getHeight()));
        stage.setMaximized(true);
        root.setCenter(new Label("Preparando Nuevo Hogar…"));
        stage.show();
        javafx.concurrent.Task<ServicioInmobiliaria> init =
                new javafx.concurrent.Task<>() {
                    protected ServicioInmobiliaria call() throws Exception {
                        ConexionBaseDatos db = new ConexionBaseDatos();
                        db.initialize();
                        DatosDemostracion.seed(db);
                        return new ServicioInmobiliaria(db);
                    }
                };
        init.setOnSucceeded(
                e -> {
                    service = init.getValue();
                    shell();
                    if (service.needsAdmin()) bootstrap();
                    else login();
                });
        init.setOnFailed(
                e -> {
                    VBox box =
                            new VBox(
                                    18,
                                    title("No pudimos abrir la base de datos"),
                                    label(
                                            "No se pudo preparar la base configurada. Consultá el detalle"
                                                    + " del aviso antes de reintentar.",
                                            "subtitle"),
                                    button("Reintentar", () -> start(stage)));
                    box.getStyleClass().add("page");
                    root.setCenter(box);
                    error(init.getException());
                });
        Thread t = new Thread(init, "inicio-base-datos");
        t.setDaemon(true);
        t.start();
    }

    private void shell() {
        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("topbar");
        VBox brand =
                new VBox(
                        0,
                        label("⌂  Nuevo Hogar", "brand"),
                        label("Plataforma de gestión inmobiliaria", "footer"));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        header.getChildren().addAll(brand, gap);
        if (!service.signedIn()) {
            root.setTop(header);
            root.setLeft(null);
            return;
        }
        if (service.role() == 1) {
            header.getChildren().addAll(
                    label("◉", "admin-avatar"),
                    new VBox(2, label("Admin", "admin-name"), label("Administrador", "admin-caption")),
                    button("↻ Recargar", this::reloadCurrentPage),
                    secondary("Notificaciones", this::notifications),
                    button("Cerrar sesión", () -> {
                        service.logout();
                        shell();
                        login();
                    }));
            root.setTop(header);
            VBox nav = new VBox(9);
            nav.setPrefWidth(238);
            nav.getStyleClass().add("sidebar");
            nav.getChildren().addAll(label("NUEVO HOGAR", "admin-side-brand"),
                    label("PANEL DE ADMINISTRACIÓN", "admin-side-caption"));
            List<ElementoMenuPanel> adminMenu = PanelAdministrador.menu(
                    this::dashboard, this::clients, this::propertyList, this::requests,
                    this::visits, this::offersAndReservations, this::followups, this::operations,
                    this::agents, this::users, this::reports, this::notifications, this::agreements,
                    this::buildings, this::settings, this::consultations, this::auditPage);
            for (ElementoMenuPanel item : adminMenu) {
                if (item.seccion()) nav.getChildren().add(label(item.nombre(), "admin-side-caption"));
                else {
                    String[] parts = item.nombre().split("\\|", 2);
                    nav.getChildren().add(adminNav(parts[0], parts[1], item.accion(), item.activo()));
                }
            }
            Region spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);
            nav.getChildren().addAll(spacer, label("Gestioná y hacé crecer\nla inmobiliaria.", "admin-side-foot"));
            ScrollPane navScroll = new ScrollPane(nav);
            navScroll.setFitToWidth(true);
            navScroll.setPrefWidth(238);
            navScroll.setStyle("-fx-background-color: #163d53;");
            root.setLeft(navScroll);
            return;
        }
        if (service.signedIn()) {
            header.getChildren().addAll(
                            button("↻ Recargar", this::reloadCurrentPage),
                            button("Mi perfil", () -> {
                                currentPageReload = this::profile;
                                profile();
                            }),
                            label(
                                    service.current().text("nombre") + " · " + roleName(),
                                    "subtitle"),
                            secondary(
                                    "Cerrar sesión",
                                    () -> {
                                        service.logout();
                                        shell();
                                        login();
                                    }));
        }
        root.setTop(header);
        if (!service.signedIn()) {
            root.setLeft(null);
            return;
        }
        VBox nav = new VBox(8);
        nav.setPrefWidth(218);
        nav.getStyleClass().add("sidebar");
        nav.getChildren().add(label("ESPACIO " + roleName().toUpperCase(Locale.ROOT), "footer"));
        List<ElementoMenuPanel> roleMenu = service.role() == 2
                ? PanelAgente.menu(this::dashboard, this::propertyList, this::requests, this::visits,
                        this::offersAndReservations, this::followups, this::clients, this::agreements,
                        this::buildings, this::operations, this::reports, this::notifications,
                        this::consultations)
                : PanelCliente.menu(this::dashboard, this::propertyList, this::requests, this::visits,
                        this::offersAndReservations,
                        () -> interests(service.current().id("id_usuario")),
                        () -> preferences(service.current().id("id_usuario")), this::notifications,
                        () -> propertyForm(null));
        for (ElementoMenuPanel item : roleMenu)
            nav.getChildren().add(button(item.nombre(), () -> {
                currentPageReload = item.accion();
                item.accion().run();
            }));
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        nav.getChildren().add(spacer);
        if (service.role() != 3)
            nav.getChildren()
                    .addAll(
                            house(170, 105, 1),
                            label(
                                    "Grandes oportunidades\ncomienzan con una\nbuena gestión.",
                                    "footer"));
        ScrollPane navScroll = new ScrollPane(nav);
        navScroll.setFitToWidth(true);
        navScroll.setPrefWidth(218);
        navScroll.setStyle("-fx-background-color: #244f65;");
        root.setLeft(navScroll);
    }

    private String roleName() {
        return switch (service.role()) {
            case 1 -> "Admin";
            case 2 -> "Agente";
            case 3 -> "Cliente";
            default -> "Invitado";
        };
    }

    private static Label label(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        l.setWrapText(true);
        return l;
    }

    private static Label title(String text) {
        return label(text, "page-title");
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.setOnAction(e -> safe(action));
        return b;
    }

    private Button secondary(String text, Runnable action) {
        Button b = button(text, action);
        b.getStyleClass().add("secondary");
        return b;
    }

    private Button adminNav(String icon, String text, Runnable action, boolean active) {
        Button button = new Button(icon + "    " + text);
        button.getStyleClass().add("admin-nav-item");
        if (active) button.getStyleClass().add("active");
        button.setOnAction(e -> {
            if (root.getLeft() instanceof ScrollPane scroll && scroll.getContent() instanceof VBox menu)
                for (Node node : menu.getChildren())
                    if (node instanceof Button item) item.getStyleClass().remove("active");
            button.getStyleClass().add("active");
            currentPageReload = action;
            safe(action);
        });
        return button;
    }

    private void reloadCurrentPage() {
        if (currentPageReload == null) {
            info("Elegí primero una sección para actualizar.");
            return;
        }
        safe(currentPageReload);
    }

    private void safe(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            error(e);
        }
    }

    private void error(Throwable e) {
        e.printStackTrace();
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.initOwner(stage);
        a.setTitle("Revisá los datos");
        a.setHeaderText("No se completó la acción");
        Throwable cause = e;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = Objects.toString(e.getMessage(), "Ocurrió un error inesperado.");
        if (cause instanceof SQLException sql) {
            message = "23505".equals(sql.getSQLState())
                    ? "Ya existe un registro con esos datos. No se pudo completar la operación."
                    : "No se pudo completar la operación con la base de datos.";
        }
        if (cause instanceof SQLException || cause != e || message.length() > 350) {
            TextArea details = new TextArea(Objects.toString(cause.getMessage(), e.toString()));
            details.setEditable(false);
            details.setWrapText(true);
            details.setPrefRowCount(6);
            details.setPrefHeight(160);
            details.setMaxHeight(160);
            a.getDialogPane().setExpandableContent(details);
            a.getDialogPane().setExpanded(false);
            if (message.length() > 350) message = "No se pudo completar la acción. Consultá los detalles.";
        }
        a.setContentText(message);
        a.setResizable(true);
        a.getDialogPane().setPrefWidth(560);
        styleDialog(a);
        a.showAndWait();
    }

    private void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.initOwner(stage);
        a.setTitle("Nuevo Hogar");
        a.setHeaderText(null);
        styleDialog(a);
        a.showAndWait();
    }

    /** Avisos sin gráficos que parezcan botones y con acciones siempre legibles. */
    private void styleDialog(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        String stylesheet = Objects.requireNonNull(getClass().getResource("/theme.css")).toExternalForm();
        if (!pane.getStylesheets().contains(stylesheet)) pane.getStylesheets().add(stylesheet);
        pane.getStyleClass().add("app-dialog");
        pane.setGraphic(null);
        for (ButtonType type : pane.getButtonTypes()) {
            if (pane.lookupButton(type) instanceof Button button) {
                if (type == ButtonType.OK) button.setText("Aceptar");
                else if (type == ButtonType.CANCEL) button.setText("Cancelar");
                else if (type == ButtonType.YES) button.setText("Sí");
                else if (type == ButtonType.NO) button.setText("No");
                button.setMinWidth(Region.USE_PREF_SIZE);
                button.setMinHeight(40);
                if (type.getButtonData().isCancelButton()) button.getStyleClass().add("secondary");
            }
        }
    }

    private void page(String title, String subtitle) {
        stopActiveMedia();
        activePage = title;
        content = new VBox(22);
        content.getStyleClass().add("page");
        content.getChildren().add(new VBox(7, title(title), label(subtitle, "subtitle")));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        root.setCenter(scroll);
    }

    private static VBox card(Node... nodes) {
        VBox v = new VBox(13, nodes);
        v.getStyleClass().add("card");
        return v;
    }

    private static HBox row(Node... nodes) {
        HBox h = new HBox(12, nodes);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static void showOnlyWhenSelected(CheckBox selected, Node... fields) {
        for (Node field : fields) {
            field.visibleProperty().bind(selected.selectedProperty());
            field.managedProperty().bind(selected.selectedProperty());
        }
    }

    private static FlowPane flow(Node... nodes) {
        FlowPane f = new FlowPane(12, 12, nodes);
        return f;
    }

    private void dialog(String title, Node body, String action, Runnable save) {
        Dialog<Void> d = new Dialog<>();
        d.initOwner(stage);
        d.setTitle(title);
        d.setResizable(true);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(730);
        scroll.setPrefViewportHeight(Math.min(620, stage.getHeight() - 200));
        d.getDialogPane().setContent(scroll);
        d.getDialogPane()
                .getStylesheets()
                .add(getClass().getResource("/theme.css").toExternalForm());
        ButtonType ok = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        d.getDialogPane()
                .lookupButton(ok)
                .addEventFilter(
                        javafx.event.ActionEvent.ACTION,
                        e -> {
                            e.consume();
                            try {
                                save.run();
                                d.close();
                            } catch (Exception ex) {
                                error(ex);
                            }
                        });
        styleDialog(d);
        d.showAndWait();
    }

    private static Pane house(double width, double height, int variant) {
        Pane p = new Pane();
        p.setPrefSize(width, height);
        p.setMinSize(width, height);
        p.setMaxSize(width, height);
        Rectangle sky =
                new Rectangle(width, height, Color.web(variant % 2 == 0 ? "#dae5df" : "#dfe6e4"));
        sky.setArcHeight(10);
        sky.setArcWidth(10);
        Rectangle lawn = new Rectangle(0, height * .77, width, height * .23);
        lawn.setFill(Color.web("#9bb9a0"));
        Rectangle wall = new Rectangle(width * .18, height * .34, width * .60, height * .48);
        wall.setFill(Color.web("#fbf4df"));
        Polygon roof =
                new Polygon(
                        width * .12,
                        height * .37,
                        width * .49,
                        height * .08,
                        width * .85,
                        height * .37);
        roof.setFill(Color.web("#426879"));
        Rectangle door = new Rectangle(width * .44, height * .54, width * .12, height * .28);
        door.setFill(Color.web("#ba9166"));
        p.getChildren().addAll(sky, lawn, wall, roof, door);
        for (double x : new double[] {.25, .62}) {
            Rectangle window = new Rectangle(width * x, height * .47, width * .10, height * .14);
            window.setFill(Color.web("#8fbbc5"));
            p.getChildren().add(window);
        }
        Circle tree = new Circle(width * .9, height * .48, height * .16, Color.web("#729987"));
        p.getChildren().add(tree);
        p.setAccessibleText("Ilustración de una vivienda; no es una fotografía del inmueble");
        return p;
    }

    private TableView<Registro> table(List<Registro> rows, String... cols) {
        TableView<Registro> t = new TableView<>(FXCollections.observableArrayList(rows));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        for (int i = 0; i < cols.length; i += 2) {
            String key = cols[i];
            TableColumn<Registro, String> c = new TableColumn<>(cols[i + 1]);
            c.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().text(key)));
            t.getColumns().add(c);
        }
        t.setPrefHeight(Math.max(140, Math.min(410, rows.size() * 40 + 48)));
        t.setPlaceholder(new Label("Todavía no hay registros para mostrar."));
        return t;
    }

    private Registro selected(TableView<Registro> t) {
        Registro r = t.getSelectionModel().getSelectedItem();
        if (r == null) throw new IllegalArgumentException("Seleccioná una fila de la tabla.");
        return r;
    }

    private static int number(String text) {
        return text == null || text.isBlank() ? 0 : Integer.parseInt(text);
    }

    private static BigDecimal money(String text) {
        return new BigDecimal(text.trim().replace(',', '.'));
    }

    private void bootstrap() {
        page(
                "Bienvenido a Nuevo Hogar",
                "Creá el administrador inicial de esta base. No hay contraseñas predeterminadas en"
                        + " MySQL.");
        Formulario f = userForm();
        PasswordField password = f.password("password", "Contraseña (mínimo 10 caracteres)");
        content.getChildren()
                .add(
                        card(
                                f,
                                button(
                                        "Crear administrador",
                                        () -> {
                                            service.bootstrapAdmin(f.values(), password.getText());
                                            service.login(f.value("email"), password.getText());
                                            shell();
                                            dashboard();
                                        })));
    }

    private Formulario userForm() {
        Formulario f = new Formulario();
        f.text("nombre", "Nombre *", "");
        f.text("apellido", "Apellido *", "");
        f.choice("tipo_documento", "Tipo de documento", "DNI", "DNI", "Pasaporte", "Otro");
        f.text("dni", "Número de documento *", "");
        f.text("email", "Correo electrónico *", "");
        f.text("telefono", "Teléfono", "");
        return f;
    }

    private void login() {
        activePage = "Iniciar sesión";
        root.setTop(loginHeader());
        root.setLeft(null);
        Formulario f = new Formulario(1);
        f.text("email", "Correo electrónico", "");
        PasswordField password = new PasswordField();
        password.setPromptText("Contraseña");
        password.setAccessibleText("Contraseña");
        TextField visiblePassword = new TextField();
        visiblePassword.setPromptText("Contraseña");
        visiblePassword.setAccessibleText("Contraseña visible");
        visiblePassword.textProperty().bindBidirectional(password.textProperty());
        visiblePassword.setVisible(false);
        visiblePassword.setManaged(false);
        ToggleButton revealPassword = new ToggleButton("Mostrar");
        revealPassword.getStyleClass().add("password-toggle");
        revealPassword.setAccessibleText("Mostrar u ocultar la contraseña");
        revealPassword.setOnAction(event -> {
            boolean visible = revealPassword.isSelected();
            password.setVisible(!visible);
            password.setManaged(!visible);
            visiblePassword.setVisible(visible);
            visiblePassword.setManaged(visible);
            revealPassword.setText(visible ? "Ocultar" : "Mostrar");
        });
        StackPane passwordFields = new StackPane(password, visiblePassword);
        HBox passwordRow = new HBox(8, passwordFields, revealPassword);
        HBox.setHgrow(passwordFields, Priority.ALWAYS);
        VBox passwordInput = new VBox(6, label("Contraseña", "field-label"), passwordRow);
        password.getStyleClass().add("login-password-input");
        visiblePassword.getStyleClass().add("login-password-input");
        VBox form = new VBox(14);
        form.getStyleClass().add("login-card");
        form.setMaxWidth(430);
        form.setPrefWidth(430);
        form.setMaxHeight(Region.USE_PREF_SIZE);
        Button submit = button(
                "Iniciar sesión",
                () -> {
                    service.login(f.value("email"), password.getText());
                    shell();
                    dashboard();
                });
        Button forgotPassword = secondary("¿Olvidaste tu contraseña?", this::forgotPassword);
        form.setSpacing(9);
        f.setHgap(0);
        f.setVgap(14);
        submit.setMaxWidth(Double.MAX_VALUE);
        forgotPassword.setMaxWidth(Double.MAX_VALUE);
        submit.setAlignment(Pos.CENTER_LEFT);
        forgotPassword.setAlignment(Pos.CENTER_RIGHT);
        forgotPassword.getStyleClass().add("text-link");
        form.getChildren().addAll(
                label("Iniciar sesión", "section-title"),
                f,
                passwordInput,
                forgotPassword,
                submit);
        if (service.db.demo)
            form.getChildren().add(
                    label(
                            "Demostración: admin@nuevohogar.demo · agente@nuevohogar.demo · "
                                    + "cliente@nuevohogar.demo\nContraseña: "
                                    + DatosDemostracion.PASSWORD,
                            "subtitle"));
        StackPane center = new StackPane();
        center.setAlignment(Pos.CENTER);
        center.getStyleClass().add("login-background");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(center.widthProperty());
        clip.heightProperty().bind(center.heightProperty());
        center.setClip(clip);
        var backgroundResource = getClass().getResource("/images/casita.jpg");
        if (backgroundResource != null) {
            ImageView background = new ImageView(new Image(backgroundResource.toExternalForm()));
            // El fondo no debe influir en el tamaño del StackPane ni desplazar el formulario.
            background.setManaged(false);
            background.setMouseTransparent(true);
            background.setPreserveRatio(true);
            background.setSmooth(true);
            Runnable ajustarFondo = () -> {
                double ancho = center.getWidth();
                double alto = center.getHeight();
                if (ancho <= 0 || alto <= 0 || background.getImage().getWidth() <= 0) return;
                double escala = Math.max(
                        ancho / background.getImage().getWidth(),
                        alto / background.getImage().getHeight());
                double anchoImagen = background.getImage().getWidth() * escala;
                double altoImagen = background.getImage().getHeight() * escala;
                background.setFitWidth(anchoImagen);
                background.setFitHeight(altoImagen);
                background.relocate((ancho - anchoImagen) / 2, (alto - altoImagen) / 2);
            };
            center.widthProperty().addListener((obs, anterior, actual) -> ajustarFondo.run());
            center.heightProperty().addListener((obs, anterior, actual) -> ajustarFondo.run());
            Rectangle tint = new Rectangle();
            tint.widthProperty().bind(center.widthProperty());
            tint.heightProperty().bind(center.heightProperty());
            tint.setFill(Color.rgb(20, 38, 48, 0.22));
            tint.setManaged(false);
            tint.setMouseTransparent(true);
            center.getChildren().addAll(background, tint);
            ajustarFondo.run();
        }
        center.getChildren().add(form);
        root.setCenter(center);
    }

    private HBox loginHeader() {
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("login-topbar");

        Label logo = label("⌂", "login-brand-mark");
        VBox name = new VBox(
                1,
                label("INMOBILIARIA", "login-brand-kicker"),
                label("Nuevo Hogar", "login-brand-name"),
                label("Gestión inmobiliaria", "login-brand-tagline"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label services = label("ADMINISTRACIÓN  ·  AGENTES  ·  CARTERA", "login-header-note");
        header.getChildren().addAll(logo, name, spacer, services);
        return header;
    }

    private void forgotPassword() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
        alert.initOwner(stage);
        alert.setTitle("Ayuda para recuperar el acceso");
        alert.setHeaderText("Recuperá tu cuenta fácilmente");
        alert.setContentText(
                "Por ahora, la aplicación no envía correos de recuperación. Para volver a entrar:\n\n"
                        + "1. Contactá al administrador de la inmobiliaria y decile el correo de tu cuenta.\n"
                        + "2. El administrador abrirá Usuarios, buscará tu cuenta y elegirá "
                        + "\"Restablecer contraseña\".\n"
                        + "3. Te dará una contraseña temporal para iniciar sesión.\n"
                        + "4. Cuando recuperes el acceso, cambiá esa contraseña desde tu perfil.\n\n"
                        + "Si la cuenta es la de Admin, contactá al responsable técnico del sistema.");
        styleDialog(alert);
        alert.showAndWait();
    }

    private StackPane catalogPhoto(Registro property) {
        StackPane frame = new StackPane();
        frame.setMinWidth(0);
        frame.setMinHeight(172); frame.setPrefHeight(172); frame.setMaxHeight(172);
        frame.getStyleClass().add("catalog-photo");
        List<String> paths = propertyImageUris(property);
        Image photo = paths.isEmpty() ? null : new Image(paths.getFirst(), 1200, 800, true, true, true);
        if (photo != null && !photo.isError()) {
            ImageView view = new ImageView(photo);
            view.fitWidthProperty().bind(frame.widthProperty()); view.setFitHeight(172);
            Runnable crop = () -> {
                double ratio = Math.max(1, frame.getWidth()) / 172.0;
                double width = Math.min(photo.getWidth(), photo.getHeight() * ratio);
                double height = width / ratio;
                view.setViewport(new Rectangle2D((photo.getWidth() - width) / 2,
                        (photo.getHeight() - height) / 2, width, height));
            };
            frame.widthProperty().addListener((o, a, b) -> crop.run()); crop.run();
            frame.getChildren().add(view);
        } else {
            SVGPath outline = new SVGPath();
            outline.setContent("M 8 52 L 52 16 L 96 52 M 20 44 L 20 98 L 84 98 L 84 44 M 43 98 L 43 68 L 61 68 L 61 98 M 28 54 L 36 54 L 36 63 L 28 63 Z M 68 54 L 76 54 L 76 63 L 68 63 Z");
            outline.getStyleClass().add("catalog-placeholder-icon");
            outline.setScaleX(.58); outline.setScaleY(.58);
            StackPane icon = new StackPane(outline); icon.setMaxHeight(65);
            VBox empty = new VBox(2, icon, label("Fotografías próximamente", "catalog-photo-note"));
            empty.setAlignment(Pos.CENTER);
            frame.getChildren().add(empty);
        }
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(frame.widthProperty()); clip.setHeight(172);
        clip.setArcWidth(20); clip.setArcHeight(20); frame.setClip(clip);
        return frame;
    }

    private Map<Integer, CheckBox> featureChecks(Set<Integer> selected) {
        Map<Integer, CheckBox> map = new LinkedHashMap<>();
        for (Registro r : service.lookup("caracteristicas")) {
            CheckBox c = new CheckBox(r.text("etiqueta"));
            c.setSelected(selected.contains(r.id("id")));
            map.put(r.id("id"), c);
        }
        return map;
    }

    private static Set<Integer> checked(Map<Integer, CheckBox> map) {
        Set<Integer> s = new HashSet<>();
        map.forEach(
                (id, c) -> {
                    if (c.isSelected()) s.add(id);
                });
        return s;
    }

    private List<String> propertyImageUris(Registro property) {
        return service.attachments(property.id("id_propiedad"), false).stream()
                .filter(file -> file.text("tipo").equals("Imagen"))
                .map(file -> {
                    try {
                        String stored = file.text("archivo");
                        if (stored.startsWith("https://") || stored.startsWith("http://")) return stored;
                        Path path = service.attachmentPath(property.id("id_propiedad"), file.id("id"), false);
                        return Files.isRegularFile(path) ? path.toUri().toString() : null;
                    } catch (IllegalArgumentException e) {
                        // Un adjunto antiguo o no disponible no debe impedir mostrar la propiedad.
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Node photoFrame(String uri, double width, double height) {
        Image image = new Image(uri, width, height, true, true, true);
        if (image.isError()) return house(width, height, uri.hashCode());
        ImageView view = new ImageView(image);
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        StackPane frame = new StackPane(view);
        frame.setMinSize(width, height);
        frame.setPrefSize(width, height);
        frame.setMaxSize(width, height);
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        frame.setClip(clip);
        frame.getStyleClass().add("property-photo-frame");
        return frame;
    }

    private Node propertyVisual(Registro property, double width, double height) {
        List<String> photos = propertyImageUris(property);
        return photos.isEmpty()
                ? house(width, height, property.id("id_propiedad"))
                : photoFrame(photos.getFirst(), width, height);
    }

    private Node propertyGallery(Registro property, double width, double height) {
        List<ElementoGaleria> items = service.attachments(property.id("id_propiedad"), false).stream()
                .filter(file -> file.text("tipo").equals("Imagen") || file.text("tipo").equals("Video"))
                .map(file -> {
                    try {
                        String stored = file.text("archivo");
                        String uri;
                        if (stored.startsWith("https://") || stored.startsWith("http://")) uri = stored;
                        else {
                            Path path = service.attachmentPath(property.id("id_propiedad"), file.id("id"), false);
                            if (!Files.isRegularFile(path)) return null;
                            uri = path.toUri().toString();
                        }
                        return new ElementoGaleria(uri, file.text("tipo").equals("Video"));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        StackPane main = new StackPane();
        main.setPrefSize(width, height);
        main.setMinSize(width, height);
        main.setMaxSize(width, height);
        main.getStyleClass().add("detail-gallery-main");
        if (items.isEmpty()) {
            Label empty = label("FOTOGRAFÍAS PENDIENTES", "photo-empty-badge");
            main.getChildren().addAll(house(width, height, property.id("id_propiedad")), empty);
            StackPane.setAlignment(empty, Pos.TOP_LEFT);
            StackPane.setMargin(empty, new Insets(14));
        } else {
            showGalleryItem(main, items.getFirst(), width, height);
        }
        HBox thumbnails = new HBox(9);
        thumbnails.getStyleClass().add("property-thumbnails");
        for (ElementoGaleria item : items) {
            StackPane thumbnail = new StackPane();
            if (item.video()) {
                thumbnail.getStyleClass().add("gallery-video-thumb");
                thumbnail.getChildren().add(label("▶  VIDEO", "gallery-video-label"));
            } else {
                thumbnail.getChildren().add(photoFrame(item.uri(), 92, 64));
            }
            thumbnail.getStyleClass().add("property-thumbnail");
            thumbnail.setOnMouseClicked(e -> showGalleryItem(main, item, width, height));
            thumbnails.getChildren().add(thumbnail);
        }
        ScrollPane thumbnailScroll = new ScrollPane(thumbnails);
        thumbnailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        thumbnailScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        thumbnailScroll.setFitToHeight(true);
        thumbnailScroll.setPrefHeight(78);
        thumbnailScroll.getStyleClass().add("gallery-thumbnail-scroll");
        VBox gallery = new VBox(10, main, thumbnailScroll);
        gallery.getStyleClass().add("property-gallery");
        return gallery;
    }

    private void showGalleryItem(StackPane main, ElementoGaleria item, double width, double height) {
        stopActiveMedia();
        String uri = item.uri();
        if (!item.video()) {
            main.getChildren().setAll(photoFrame(uri, width, height));
            return;
        }
        try {
            Media media = new Media(uri);
            MediaPlayer player = new MediaPlayer(media);
            activeMediaPlayer = player;
            MediaView view = new MediaView(player);
            view.setPreserveRatio(true);
            view.fitWidthProperty().bind(main.widthProperty());
            view.fitHeightProperty().bind(main.heightProperty());
            StackPane videoSurface = new StackPane(view);
            videoSurface.setStyle("-fx-background-color: #101820;");
            Button play = secondary("Cargando…", () -> {});
            play.setDisable(true);
            boolean[] ended = {false};
            play.setOnAction(e -> {
                if (player.getStatus() == MediaPlayer.Status.PLAYING && !ended[0]) {
                    player.pause();
                } else {
                    if (ended[0]) {
                        player.seek(Duration.ZERO);
                        ended[0] = false;
                    }
                    player.play();
                    play.setText("Ⅱ  Pausar");
                }
            });
            Slider progress = new Slider(0, 1, 0);
            progress.setDisable(true);
            Label time = label("00:00 / --:--", "gallery-video-time");
            Runnable ready = () -> {
                if (activeMediaPlayer != player) return;
                Duration total = player.getTotalDuration();
                boolean seekable = !total.isUnknown() && !total.isIndefinite() && total.greaterThan(Duration.ZERO);
                progress.setMax(seekable ? total.toSeconds() : 1);
                progress.setDisable(!seekable);
                play.setDisable(false);
                play.setText("▶  Reproducir");
                time.setText("00:00 / " + mediaTime(total));
            };
            player.setOnReady(ready);
            player.setOnPlaying(() -> play.setText("Ⅱ  Pausar"));
            player.setOnPaused(() -> play.setText("▶  Reproducir"));
            player.setOnEndOfMedia(() -> {
                ended[0] = true;
                play.setText("↻  Volver a reproducir");
            });
            player.currentTimeProperty().addListener((obs, old, current) -> {
                if (!progress.isValueChanging() && !progress.isPressed()) progress.setValue(current.toSeconds());
                time.setText(mediaTime(current) + " / " + mediaTime(player.getTotalDuration()));
            });
            progress.setOnMouseReleased(e -> {
                ended[0] = false;
                player.seek(Duration.seconds(progress.getValue()));
            });
            Runnable failed = () -> {
                if (activeMediaPlayer != player) return;
                Throwable failure = player.getError() != null ? player.getError() : media.getError();
                if (failure != null) failure.printStackTrace();
                stopActiveMedia();
                showVideoUnavailable(main, uri);
            };
            player.setOnError(failed);
            player.setOnHalted(failed);
            media.setOnError(failed);
            HBox controls = new HBox(12, play, progress, time);
            controls.setAlignment(Pos.CENTER);
            controls.setMaxHeight(Region.USE_PREF_SIZE);
            HBox.setHgrow(progress, Priority.ALWAYS);
            controls.getStyleClass().add("gallery-video-controls");
            StackPane.setAlignment(controls, Pos.BOTTOM_CENTER);
            videoSurface.getChildren().add(controls);
            main.getChildren().setAll(videoSurface);
            if (player.getError() != null || media.getError() != null) failed.run();
            else {
                if (player.getStatus() == MediaPlayer.Status.READY) ready.run();
                player.setAutoPlay(true);
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            stopActiveMedia();
            showVideoUnavailable(main, uri);
        }
    }

    private void showVideoUnavailable(StackPane main, String uri) {
        Label message = label("No se pudo reproducir este video dentro del sistema. "
                + "Para verlo aquí, usá MP4 con video H.264 y audio AAC. "
                + "También podés abrir el archivo con el reproductor de tu equipo.", "subtitle");
        message.setWrapText(true);
        message.setMaxWidth(460);
        VBox fallback = new VBox(16, message, secondary("Abrir video en el equipo", () -> safe(() -> {
            URI source = URI.create(uri);
            if ("file".equalsIgnoreCase(source.getScheme()) && !Files.isRegularFile(Path.of(source)))
                throw new IllegalArgumentException("El archivo de video no está disponible en esta computadora.");
            getHostServices().showDocument(uri);
        })));
        fallback.setAlignment(Pos.CENTER);
        fallback.setPadding(new Insets(24));
        main.getChildren().setAll(fallback);
    }

    private static String mediaTime(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.isIndefinite()) return "--:--";
        long seconds = Math.max(0, (long) duration.toSeconds());
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void stopActiveMedia() {
        if (activeMediaPlayer != null) {
            activeMediaPlayer.stop();
            activeMediaPlayer.dispose();
            activeMediaPlayer = null;
        }
    }

    private VBox propertyCard(Registro property) {
        return propertyCard(property, 0);
    }

    private VBox propertyCard(Registro property, int operationFilter) {
        int id = property.id("id_propiedad");
        List<Registro> offers = service.offers(id);
        Registro offer = offers.stream()
                .filter(row -> operationFilter == 0 || row.id("id_tipo_transaccion") == operationFilter)
                .findFirst().orElse(offers.isEmpty() ? null : offers.getFirst());
        String operation = offer == null ? "Consultar" : offer.text("operacion");

        StackPane image = catalogPhoto(property);
        Label badge = label(operation, "property-operation-badge");
        StackPane.setAlignment(badge, Pos.TOP_LEFT);
        StackPane.setMargin(badge, new Insets(12));
        image.getChildren().add(badge);

        Label title = label(property.text("titulo"), "property-card-title");
        title.setMinHeight(42);
        title.setPrefHeight(42);
        title.setMaxHeight(42);
        Tooltip.install(title, new Tooltip(property.text("titulo")));
        Label location = label("⌖  " + property.text("localidad"), "property-card-location");
        location.setMinHeight(28);
        location.setMaxHeight(28);
        HBox facts = new HBox(12,
                label(metric(property, "dormitorios", " dorm."), "property-fact-value"),
                label(metric(property, "banos", " baños"), "property-fact-value"),
                label(metric(property, "superficie_total_m2", " m²"), "property-fact-value"));
        facts.getStyleClass().add("property-card-facts");
        Label price = label(offer == null ? "Consultar precio" : formatOffer(offer), "property-card-price");
        Button details = button("Ver detalle  →", () -> propertyDetails(id));
        details.getStyleClass().add("property-detail-button");
        details.setMaxWidth(Double.MAX_VALUE);

        Region space = new Region();
        VBox.setVgrow(space, Priority.ALWAYS);
        VBox body = new VBox(8, label(property.text("tipo"), "property-type"), title, location, facts, space, price, details);
        VBox.setVgrow(body, Priority.ALWAYS);
        body.getStyleClass().add("property-card-body");
        VBox card = new VBox(0, image, body);
        card.getStyleClass().add("property-card");
        card.setPrefWidth(260);
        card.setMinWidth(260);
        card.setMaxWidth(260);
        card.setMinHeight(370);
        return card;
    }

    private HBox propertyMiniFact(String icon, String value) {
        HBox item = new HBox(5, label(icon, "property-fact-icon"), label(value, "property-fact-value"));
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private String metric(Registro property, String key, String suffix) {
        String value = property.text(key);
        if (value.isBlank() || "0".equals(value) || "0.00".equals(value)) return "—";
        try {
            return formatNumber(new BigDecimal(value)) + suffix;
        } catch (NumberFormatException ignored) {
            return value + suffix;
        }
    }

    private String formatOffer(Registro offer) {
        String value = offer.text("moneda") + " " + formatNumber(new BigDecimal(offer.text("precio")));
        return offer.id("id_tipo_transaccion") == 2 ? value + " / mes" : value;
    }

    private String formatNumber(BigDecimal value) {
        java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(new Locale("es", "AR"));
        format.setGroupingUsed(true);
        format.setMaximumFractionDigits(Math.min(2, Math.max(0, value.stripTrailingZeros().scale())));
        return format.format(value);
    }

    private void propertyDetails(int id) {
        Registro property = service.property(id);
        List<Registro> offers = service.offers(id);
        List<Registro> characteristics = service.features(id);
        page(property.text("titulo"), property.text("direccion") + " · " + property.text("localidad"));

        Button back = secondary("←  Volver a mis propiedades", this::propertyList);
        VBox facts = new VBox(14);
        facts.getStyleClass().add("property-detail-facts");
        facts.getChildren().addAll(
                row(label(property.text("tipo"), "property-type"), label(property.text("comercial"), "property-status-badge")),
                label("⌖  " + property.text("localidad"), "property-detail-location"));
        for (Registro offer : offers) {
            HBox priceLine = new HBox(12, label(offer.text("operacion"), "property-price-caption"), label(formatOffer(offer), "property-detail-price"));
            priceLine.setAlignment(Pos.CENTER_LEFT);
            facts.getChildren().add(priceLine);
            if (service.role() != 3 && offer.id("id_tipo_transaccion") == 1
                    && offer.get("precio_minimo") != null)
                facts.getChildren().add(label("Mínimo interno de venta: " + offer.text("moneda")
                        + " " + formatNumber(offer.money("precio_minimo")), "subtitle"));
        }
        TilePane specifications = new TilePane(12, 12,
                propertyDetailFact("⌂", metric(property, "dormitorios", ""), "dormitorios"),
                propertyDetailFact("♧", metric(property, "banos", ""), "baños"),
                propertyDetailFact("▧", metric(property, "superficie_total_m2", " m²"), "totales"),
                propertyDetailFact("▧", metric(property, "superficie_cubierta_m2", " m²"), "cubiertos"));
        specifications.setPrefColumns(2);
        specifications.setPrefTileWidth(170);
        specifications.setTileAlignment(Pos.CENTER_LEFT);
        facts.getChildren().addAll(specifications,
                label("Ambientes: " + metric(property, "ambientes", "") + "   ·   Antigüedad: " + metric(property, "antiguedad_anos", " años"), "property-extra-facts"));

        Button contact = button("Contactar", () -> contactProperty(id));
        contact.getStyleClass().add("property-detail-button");
        Button favorite = secondary("♡  Guardar", () -> saveFavorite(id));
        FlowPane actions = new FlowPane(10, 10, contact, favorite);
        if (service.signedIn() && service.role() == 3) {
            actions.getChildren().add(secondary("Solicitar visita", this::requestVisit));
            actions.getChildren().add(button("Hacer una oferta", () -> makeOffer(id)));
        }
        facts.getChildren().add(actions);

        double galleryWidth = Math.min(650, Math.max(320, stage.getScene().getWidth() - (service.role() == 3 ? 100 : 360)));
        FlowPane hero = new FlowPane(20, 20, propertyGallery(property, galleryWidth, galleryWidth * 370 / 650), facts);
        hero.setAlignment(Pos.TOP_LEFT);
        hero.prefWrapLengthProperty().bind(content.widthProperty());
        facts.setMinWidth(400);
        facts.prefWidthProperty().bind(javafx.beans.binding.Bindings.max(400, hero.widthProperty().subtract(galleryWidth + 32)));
        facts.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().addAll(back, hero);

        TabPane tabs = new TabPane();
        Tab description = new Tab("Descripción", label(property.text("descripcion"), "property-description-copy"));
        FlowPane chips = new FlowPane(8, 8);
        for (Registro characteristic : characteristics)
            chips.getChildren().add(label(characteristic.text("etiqueta"), "feature-chip"));
        if (characteristics.isEmpty()) chips.getChildren().add(label("No se especificaron características adicionales.", "subtitle"));
        Tab features = new Tab("Características", chips);
        VBox legal = new VBox(9,
                label("Superficie cubierta: " + metric(property, "superficie_cubierta_m2", " m²"), "subtitle"),
                label("Superficie total: " + metric(property, "superficie_total_m2", " m²"), "subtitle"),
                label("Expensas: " + (property.text("expensas").isBlank() || property.text("expensas").equals("0.00") ? "No informadas" : property.text("moneda") + " " + formatNumber(new BigDecimal(property.text("expensas")))), "subtitle"));
        if (property.id("id_edificio") > 0)
            legal.getChildren().add(label("Edificio #" + property.id("id_edificio") + " · Unidad " + property.text("numero_unidad"), "subtitle"));
        Tab details = new Tab("Detalles", legal);
        for (Tab tab : List.of(description, features, details)) tab.setClosable(false);
        tabs.getTabs().addAll(description, features, details);
        if (service.signedIn() && service.role() != 3) {
            Tab priceHistory = new Tab("Historial de precios", table(service.historialPrecios(id),
                    "fecha", "Fecha", "operacion", "Operación", "precio_anterior", "Antes",
                    "precio_nuevo", "Nuevo", "moneda", "Moneda", "usuario", "Registrado por", "motivo", "Motivo"));
            priceHistory.setClosable(false);
            tabs.getTabs().add(priceHistory);
        }
        tabs.setPrefHeight(220);

        VBox map = new VBox(10, label("Ubicación", "section-title"), mapPreview(),
                label(property.text("direccion") + "\n" + property.text("localidad"), "property-card-location"),
                secondary("Ver en el mapa", () -> openMap(property)));
        map.getStyleClass().add("property-map-card");
        HBox lower = new HBox(16, tabs, map);
        HBox.setHgrow(tabs, Priority.ALWAYS);
        content.getChildren().add(lower);

        if (service.canViewPrivateProperty(id)) {
            VBox docs = card(label("Documentos privados de la propiedad", "section-title"),
                    label("Documentación privada para el propietario y el equipo responsable. Disponible durante la revisión de publicación.", "subtitle"));
            docs.setId("property-documents");
            addFileButtons(docs, id, true);
            if (Set.of(1, 4).contains(property.id("id_estado_verificacion"))
                    || (service.role() != 3 && property.id("id_estado_verificacion") == 2))
                docs.getChildren().add(secondary("Subir documentación", () -> upload(id, true)));
            content.getChildren().add(propertyTaxPanel(property));
            content.getChildren()
                    .addAll(
                            docs,
                            row(
                                    button("Editar ficha", () -> propertyForm(property)),
                                    secondary("Adjuntar archivos", () -> upload(id))));
            if (service.role() != 3)
                content.getChildren()
                        .add(
                                table(
                                        service.stateHistory(id),
                                        "fecha",
                                        "Fecha",
                                        "estado",
                                        "Estado",
                                        "motivo",
                                        "Motivo"));
        }
    }

    private HBox propertyDetailFact(String icon, String value, String caption) {
        Label amount = label(value, "property-detail-fact-value");
        Label name = label(caption, "property-detail-fact-label");
        amount.setWrapText(false);
        name.setWrapText(false);
        amount.setMinWidth(Region.USE_PREF_SIZE);
        name.setMinWidth(Region.USE_PREF_SIZE);
        VBox text = new VBox(4, amount, name);
        HBox box = new HBox(12, label(icon, "property-detail-fact-icon"), text);
        box.setMinWidth(170);
        box.setPrefWidth(170);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("property-detail-fact");
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private Pane mapPreview() {
        Pane map = new Pane();
        map.setPrefSize(270, 142);
        map.setMinSize(220, 142);
        map.getStyleClass().add("map-preview");
        for (int i = 0; i < 4; i++) {
            javafx.scene.shape.Line road = new javafx.scene.shape.Line(0, 25 + i * 34, 280, 4 + i * 34);
            road.setStroke(Color.web("#ffffff")); road.setStrokeWidth(8); map.getChildren().add(road);
        }
        for (int i = 0; i < 3; i++) {
            javafx.scene.shape.Line cross = new javafx.scene.shape.Line(35 + i * 95, 0, 70 + i * 82, 150);
            cross.setStroke(Color.web("#ffffff")); cross.setStrokeWidth(6); map.getChildren().add(cross);
        }
        Circle pin = new Circle(137, 67, 12, Color.web("#327994"));
        pin.setStroke(Color.WHITE); pin.setStrokeWidth(4); map.getChildren().add(pin);
        return map;
    }

    private void openMap(Registro property) {
        String query = java.net.URLEncoder.encode(property.text("direccion") + ", " + property.text("localidad"), java.nio.charset.StandardCharsets.UTF_8);
        getHostServices().showDocument("https://www.google.com/maps/search/?api=1&query=" + query);
    }

    private void contactProperty(int id) {
        boolean registeredClient = service.signedIn() && service.role() == 3;
        if (service.signedIn() && !registeredClient) {
            info("Las consultas sobre inmuebles se envían desde el catálogo de la web.");
            return;
        }
        Formulario form = new Formulario();
        if (!registeredClient) {
            form.text("name", "Tu nombre *", "");
            form.text("email", "Correo", "");
            form.text("phone", "Teléfono", "");
        }
        form.area("message", "Consulta", "Me interesa recibir información sobre esta propiedad.");
        dialog("Contactar a la inmobiliaria", form, "Enviar consulta", () -> {
            service.createInquiry(id, registeredClient ? service.current().id("id_usuario") : null,
                    form.value("name"), form.value("email"), form.value("phone"), form.value("message"));
            info("Tu consulta quedó registrada. El equipo la verá en su panel.");
        });
    }

    private void saveFavorite(int id) {
        if (!service.signedIn()) { login(); return; }
        if (service.role() != 3) { info("Iniciá sesión como cliente para guardar propiedades."); return; }
        service.interest(service.current().id("id_usuario"), id, "Interesado", "Guardada desde favoritos.");
        info("Propiedad agregada a tus intereses.");
    }

    private String documentName(String type) {
        return switch (type) {
            case "Impuesto" -> "Comprobante de impuesto";
            case "Servicio" -> "Comprobante de servicio";
            case "Reglamento" -> "Reglamento de copropiedad";
            case "Otra" -> "Otra documentación";
            default -> type;
        };
    }

    private String documentHelp(String type) {
        return switch (type) {
            case "Plano" -> "Subí el plano de la propiedad: el dibujo que muestra sus ambientes y medidas.";
            case "Escritura" -> "Subí una copia o foto legible de la escritura de la propiedad.";
            case "Impuesto" -> "Subí una boleta o comprobante de impuesto inmobiliario o municipal. El cálculo de importes se hace en el apartado de abajo.";
            case "Servicio" -> "Subí una boleta o comprobante de luz, agua o gas de esta propiedad.";
            case "Reglamento" -> "Subí el reglamento de copropiedad del edificio o complejo.";
            case "Otra" -> "Adjuntá otro documento útil e indicá su tipo en el nombre del archivo.";
            default -> "Seleccioná una foto o un video para la galería de la propiedad.";
        };
    }

    private ComboBox<String> documentChoice(Formulario form, boolean documentsOnly) {
        ComboBox<String> choice = form.choice("tipo", "1. ¿Qué vas a subir?", documentsOnly ? "Plano" : "Imagen",
                documentsOnly ? new String[]{"Plano", "Escritura", "Impuesto", "Servicio", "Reglamento", "Otra"}
                        : new String[]{"Imagen", "Video", "Plano", "Escritura", "Impuesto", "Servicio", "Reglamento", "Otra"});
        choice.setConverter(new javafx.util.StringConverter<String>() {
            public String toString(String value) { return value == null ? "" : documentName(value); }
            public String fromString(String value) { return value; }
        });
        return choice;
    }

    private VBox pendingDocumentsPanel(Map<Path, String> pending) {
        return pendingDocumentsPanel(pending, () -> {});
    }

    private VBox pendingDocumentsPanel(Map<Path, String> pending, Runnable selectionChanged) {
        Formulario form = new Formulario(1);
        ComboBox<String> choice = documentChoice(form, true);
        Label help = label(documentHelp(choice.getValue()), "subtitle");
        choice.valueProperty().addListener((o, old, value) -> help.setText(documentHelp(value)));
        VBox list = new VBox(8);
        Label status = label("Documentos seleccionados: 0/8.", "subtitle");
        Runnable updateStatus = () -> status.setText("Documentos seleccionados: " + pending.size()
                + "/8. Se guardarán al guardar el borrador o enviar a revisión.");
        Runnable refresh = () -> {
            list.getChildren().clear();
            pending.forEach((path, type) -> {
                Label name = label(documentName(type) + " · " + path.getFileName(), "subtitle");
                name.setWrapText(true);
                name.setMaxWidth(520);
                FlowPane line = new FlowPane(10, 6);
                line.getChildren().addAll(name, secondary("Quitar de la selección", () -> {
                    pending.remove(path);
                    list.getChildren().remove(line);
                    updateStatus.run();
                    selectionChanged.run();
                }));
                list.getChildren().add(line);
            });
            updateStatus.run();
        };
        VBox box = card(label("Documentos privados de la propiedad", "section-title"),
                label("Solo pueden verlos el propietario, administración y el agente responsable. No aparecen en el catálogo público.", "subtitle"),
                form, help,
                secondary("2. Elegir archivos", () -> {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Elegir: " + documentName(choice.getValue()));
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF o fotos (JPG, PNG)", "*.pdf", "*.jpg", "*.jpeg", "*.png"));
                    var files = chooser.showOpenMultipleDialog(stage);
                    if (files != null) for (var file : files) {
                        Path path = file.toPath();
                        if (pending.containsKey(path)) continue;
                        if (pending.size() >= 8) {
                            info("Podés adjuntar hasta 8 documentos por envío.");
                            break;
                        }
                        try {
                            if (Files.size(path) > 2L * 1024 * 1024) {
                                info("Cada documento puede pesar hasta 2 MB: " + file.getName());
                                continue;
                            }
                        } catch (Exception e) {
                            info("No se pudo leer el archivo " + file.getName());
                            continue;
                        }
                        pending.put(path, choice.getValue());
                    }
                    refresh.run();
                    selectionChanged.run();
                }),
                label("Podés adjuntar hasta 8 documentos en total, de hasta 2 MB cada uno. PDF o fotos JPG/PNG. Revisá que el texto se lea bien.", "subtitle"),
                status, list);
        refresh.run();
        return box;
    }

    private Formulario taxForm() {
        Formulario form = new Formulario(2);
        form.text("nombre", "¿Qué impuesto es? *", "").setPromptText("Ej.: impuesto inmobiliario");
        form.text("periodo", "¿A qué período corresponde? *", "").setPromptText("Ej.: noviembre 2026 o cuota 3");
        form.text("base", "Valor sobre el que se calcula *", "").setPromptText("Ej.: 100000 (sin puntos de miles)");
        form.text("porcentaje", "Porcentaje a aplicar (%) *", "").setPromptText("Ej.: 2 para calcular el 2 %");
        form.choice("moneda", "Moneda", "ARS", "ARS", "USD");
        return form;
    }

    private ServicioInmobiliaria.PropertyTax readTax(Formulario form) {
        try {
            return new ServicioInmobiliaria.PropertyTax(form.value("nombre"),
                    new BigDecimal(form.value("base").replace(',', '.')),
                    new BigDecimal(form.value("porcentaje").replace(',', '.')),
                    form.value("moneda"), form.value("periodo"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ingresá números válidos en base y porcentaje, sin separadores de miles.");
        }
    }

    private VBox pendingTaxesPanel(List<ServicioInmobiliaria.PropertyTax> pending) {
        Formulario form = taxForm();
        VBox results = new VBox(10);
        return card(label("Calcular un impuesto", "section-title"),
                label("Este apartado calcula un importe; no sube boletas. Ejemplo: un valor de 100000 al 2 % da 2000. Ingresá el porcentaje que corresponda: el sistema no elige tasas oficiales.", "subtitle"),
                form, label("Completá los campos con * y pulsá Calcular y agregar. El resultado se guardará al guardar el borrador o enviar a revisión.", "subtitle"), secondary("Calcular y agregar", () -> {
                    var tax = readTax(form);
                    pending.add(tax);
                    Label value = label(tax.name() + " · " + tax.period() + " · " + tax.currency() + " " + formatNumber(tax.amount()), "section-title");
                    FlowPane line = new FlowPane(10, 8);
                    line.getChildren().addAll(value, secondary("Quitar", () -> { pending.remove(tax); results.getChildren().remove(line); }));
                    results.getChildren().add(line);
                    for (String key : List.of("nombre", "periodo", "base", "porcentaje"))
                        ((TextField) form.control(key)).clear();
                }), results);
    }

    private VBox taxSummary(Registro tax) {
        return new VBox(5,
                label(tax.text("nombre") + " · " + tax.text("periodo"), "section-title"),
                label(tax.text("moneda") + " " + tax.text("base_imponible") + " × " + tax.text("porcentaje") + "% = "
                        + tax.text("moneda") + " " + tax.text("importe"), "subtitle"));
    }

    private VBox propertyTaxPanel(Registro property) {
        int id = property.id("id_propiedad");
        VBox box = card(label("Impuestos de la propiedad", "section-title"),
                label("Estimaciones manuales para revisión interna. Los importes corresponden al período y moneda de cada cálculo.", "subtitle"));
        box.setId("property-taxes");
        List<Registro> taxes = service.propertyTaxes(id);
        if (taxes.isEmpty()) box.getChildren().add(label("Todavía no hay cálculos guardados.", "subtitle"));
        for (Registro tax : taxes) box.getChildren().add(taxSummary(tax));
        if (Set.of(1, 4).contains(property.id("id_estado_verificacion"))
                || (service.role() != 3 && property.id("id_estado_verificacion") == 2))
            box.getChildren().add(secondary("Calcular un impuesto", () -> {
                Formulario form = taxForm();
                Label result = label("Ingresá la base y el porcentaje para calcular el importe.", "subtitle");
                VBox editor = new VBox(14, form, secondary("Calcular", () -> {
                    var tax = readTax(form);
                    result.setText("Importe estimado: " + tax.currency() + " " + formatNumber(tax.amount()));
                }), result);
                dialog("Calcular impuesto", editor, "Guardar cálculo", () -> {
                    service.savePropertyTax(id, readTax(form));
                    propertyDetails(id);
                });
            }));
        return box;
    }

    private void addFileButtons(VBox box, int id, boolean docs) {
        List<Registro> files = service.attachments(id, docs);
        if (files.isEmpty())
            box.getChildren().add(label("Todavía no se cargaron archivos.", "subtitle"));
        for (Registro f : files) {
            String label = f.text("tipo") + " · " + (docs ? f.text("nombre") : "Archivo #" + f.id("id"));
            Button open = secondary(label, () -> {
                try {
                    Path path = service.attachmentPath(id, f.id("id"), docs);
                    if (!Files.isRegularFile(path))
                        throw new IllegalArgumentException("El archivo local no está disponible.");
                    getHostServices().showDocument(path.toUri().toString());
                } catch (Exception e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            });
            box.getChildren().add(open);
        }
    }

    private void upload(int id) { upload(id, false); }

    private void upload(int id, boolean documentsOnly) {
        Formulario f = new Formulario();
        ComboBox<String> choice = documentChoice(f, documentsOnly);
        Label help = label(documentHelp(choice.getValue()), "subtitle");
        choice.valueProperty().addListener((o, old, value) -> help.setText(documentHelp(value)));
        Label filename = label("Todavía no elegiste un archivo.", "subtitle");
        Path[] path = {null};
        VBox box =
                card(
                        label(documentsOnly ? "Documentos privados: solo los ven el propietario, administración y el agente responsable. PDF o fotos JPG/PNG, hasta 50 MB."
                                : "Las fotos y videos van a la galería. Los planos, escrituras y comprobantes son privados. Máximo 50 MB por archivo.", "subtitle"),
                        f, help,
                        filename,
                        secondary(
                                "2. Elegir archivo",
                                () -> {
                                    FileChooser fc = new FileChooser();
                                    fc.setTitle(documentsOnly ? "Subir planos, escrituras o comprobantes" : "Adjuntar documentación o multimedia");
                                    if (documentsOnly) fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos PDF o imágenes", "*.pdf", "*.jpg", "*.jpeg", "*.png"));
                                    java.io.File file = fc.showOpenDialog(stage);
                                    if (file != null) {
                                        path[0] = file.toPath();
                                        filename.setText("Listo para subir: " + file.getName() + ". Confirmá con el botón de abajo.");
                                    }
                                }));
        dialog(
                documentsOnly ? "Subir un documento privado" : "Adjuntar archivo",
                box,
                documentsOnly ? "Subir documento" : "Subir archivo",
                () -> {
                    if (path[0] == null)
                        throw new IllegalArgumentException("Seleccioná un archivo.");
                    try {
                        service.attach(id, path[0], f.value("tipo"));
                        propertyDetails(id);
                    } catch (Exception e) {
                        throw new IllegalArgumentException(e.getMessage(), e);
                    }
                });
    }

    private void dashboard() {
        currentPageReload = this::dashboard;
        if (service.role() != 3) service.generarAlertasDeSeguimiento();
        if (service.role() == 1) {
            adminDashboard();
            return;
        }
        List<Registro> properties = service.allProperties();
        List<Registro> requests = service.role() == 3 ? service.ownerRequests() : service.requests();
        String requestStatusKey = service.role() == 3 ? "estado_nombre" : "estado";
        page(
                "¡Hola, " + service.current().text("nombre") + "!",
                "Aquí tenés un resumen de "
                        + (service.role() == 3
                                ? "tus gestiones"
                                : "la actividad de la inmobiliaria")
                        + " · "
                        + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        FlowPane metrics = new FlowPane(16, 16);
        metrics.getChildren()
                .addAll(
                        stat("Propiedades", properties.size()),
                        stat(
                                "Publicadas",
                                properties.stream()
                                        .filter(r -> r.id("id_estado_verificacion") == 3)
                                        .filter(
                                                r ->
                                                        Set.of(1, 2)
                                                                .contains(
                                                                        r.id(
                                                                                "id_estado_comercial")))
                                        .count()),
                        stat(
                                "En revisión",
                                requests.stream()
                                        .filter(r -> r.text(requestStatusKey).equals("Pendiente"))
                                        .count()),
                        stat(
                                "Rechazadas",
                                properties.stream()
                                        .filter(r -> r.id("id_estado_verificacion") == 4)
                                        .count()));
        if (service.role() == 1) {
            metrics.getChildren()
                    .addAll(
                            stat("Clientes", service.clients().size()),
                            stat("Agentes", service.agents().size()),
                            stat("Operaciones cerradas", service.operations().size()),
                            stat(
                                    "Disponibles",
                                    properties.stream()
                                            .filter(r -> r.id("id_estado_comercial") == 1)
                                            .count()),
                            stat(
                                    "Vendidas",
                                    properties.stream()
                                            .filter(r -> r.id("id_estado_comercial") == 3)
                                            .count()),
                            stat(
                                    "Alquiladas",
                                    properties.stream()
                                            .filter(r -> r.id("id_estado_comercial") == 4)
                                            .count()));
        } else if (service.role() == 2) {
            metrics.getChildren()
                    .addAll(
                            stat("Mis clientes", service.clients().size()),
                            stat("Mis operaciones", service.operations().size()));
        } else {
            metrics.getChildren()
                    .addAll(
                            stat(
                                    "Mis solicitudes",
                                    requests.size()),
                            stat(
                                    "Mis intereses",
                                    service.interests(service.current().id("id_usuario")).size()));
        }
        content.getChildren().add(metrics);
        PieChart pie = new PieChart();
        Map<String, Long> counts = new LinkedHashMap<>();
        properties.forEach(p -> counts.merge(p.text("verificacion"), 1L, Long::sum));
        counts.forEach((k, v) -> pie.getData().add(new PieChart.Data(k + " (" + v + ")", v)));
        pie.setPrefSize(330, 260);
        pie.setLabelsVisible(false);
        pie.setTitle("Estado de las propiedades");
        TableView<Registro> t =
                table(
                        properties.stream().limit(6).toList(),
                        "id_propiedad",
                        "ID",
                        "titulo",
                        "Propiedad",
                        "localidad",
                        "Ubicación",
                        "verificacion",
                        "Publicación");
        t.setPrefWidth(580);
        VBox summary =
                card(
                        label(
                                service.role() == 3 ? "Mis propiedades" : "Cartera de propiedades",
                                "section-title"),
                        t,
                        row(
                                button(
                                        "Ver propiedad",
                                        () -> propertyDetails(selected(t).id("id_propiedad"))),
                                secondary("Ver todas", this::propertyList)));
        HBox.setHgrow(summary, Priority.ALWAYS);
        content.getChildren().add(row(summary, card(pie)));
        TableView<Registro> rq =
                table(
                        requests.stream().limit(5).toList(),
                        service.role() == 3 ? "id_solicitud" : "id_publicacion",
                        "ID",
                        "titulo",
                        "Propiedad",
                        requestStatusKey,
                        "Estado",
                        "fecha_solicitud",
                        "Fecha");
        content.getChildren()
                .add(
                        card(
                                label("Últimas solicitudes", "section-title"),
                                rq,
                                secondary("Gestionar solicitudes", this::requests)));
        if (service.role() != 3) {
            List<Registro> activity = service.activity().stream().limit(5).toList();
            content.getChildren()
                    .add(
                            card(
                                    label("Actividad reciente del equipo", "section-title"),
                                    table(
                                            activity, "fecha", "Fecha", "usuario", "Usuario",
                                            "accion", "Acción", "detalle", "Detalle")));
        }
    }

    private void adminDashboard() {
        DatosPanelAdministrador data = new PanelAdministradorDAO(service.db).load();
        page("¡Hola, Admin!", "Resumen general del sistema · "
                + LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "AR"))));

        HBox welcome = new HBox(20);
        welcome.getStyleClass().add("admin-welcome");
        welcome.setAlignment(Pos.CENTER_LEFT);
        VBox welcomeText = new VBox(7,
                label("Controlá la inmobiliaria desde un solo lugar", "admin-welcome-title"),
                label("Revisá solicitudes, seguí la cartera y gestioná el equipo.", "admin-welcome-copy"));
        Region stretch = new Region(); HBox.setHgrow(stretch, Priority.ALWAYS);
        welcome.getChildren().addAll(welcomeText, stretch,
                button("Revisar solicitudes  →", this::requests),
                secondary("Agregar usuario", () -> newUser(true)));
        content.getChildren().add(welcome);

        FlowPane metrics = new FlowPane(14, 14);
        metrics.getStyleClass().add("admin-metrics");
        long pendingOwnerRequests = service.ownerRequests().stream()
                .filter(request -> request.id("id_estado_verificacion") == 2).count();
        long pendingConsultations = service.consultations().stream()
                .filter(inquiry -> inquiry.text("estado").equals("Pendiente")).count();
        metrics.getChildren().addAll(
                adminMetric("♙", "Clientes registrados", data.clients(), "Personas en la cartera", "blue"),
                adminMetric("▤", "Solicitudes pendientes", data.pendingRequests(), "Requieren revisión", "gold"),
                adminMetric("⌂", "Propuestas de propietarios", pendingOwnerRequests, "Esperan asignación o decisión", "gold"),
                adminMetric("✉", "Consultas pendientes", pendingConsultations, "Requieren respuesta del equipo", "teal"),
                adminMetric("↗", "Operaciones cerradas", data.closedOperations(), "Ventas y alquileres", "blue"),
                adminMetric("⌂", "Alquileres activos", data.rentedProperties(), "Propiedades alquiladas", "violet"),
                adminMetric("♙", "Agentes activos", data.agents(), "" + data.totalUsers() + " usuarios activos", "teal"));
        content.getChildren().add(metrics);

        PieChart types = new PieChart();
        data.propertyTypes().forEach((name, count) -> {
            if (count > 0) types.getData().add(new PieChart.Data(name + " · " + count, count));
        });
        types.setTitle("Propiedades por tipo");
        types.setLegendVisible(true);
        types.setLabelsVisible(false);
        types.setPrefHeight(250);
        VBox typeCard = card(label("Cartera por tipo", "section-title"), types);
        typeCard.getStyleClass().add("admin-card");

        VBox states = new VBox(12,
                label("Estado de las propiedades", "section-title"),
                label(data.totalProperties() + " inmuebles registrados · " + data.publishedProperties() + " aprobados", "admin-summary-note"));
        for (Map.Entry<String, Long> entry : data.propertyStates().entrySet()) {
            HBox line = new HBox(10);
            line.setAlignment(Pos.CENTER_LEFT);
            Label name = label(entry.getKey(), "admin-state-name");
            name.setMinWidth(160);
            ProgressBar bar = new ProgressBar(data.totalProperties() == 0 ? 0 : (double) entry.getValue() / data.totalProperties());
            bar.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(bar, Priority.ALWAYS);
            line.getChildren().addAll(name, bar, label("" + entry.getValue(), "admin-state-count"));
            states.getChildren().add(line);
        }
        states.getChildren().addAll(new Separator(),
                label("" + data.pendingProperties() + " pendientes de verificación  ·  "
                        + data.rejectedProperties() + " rechazadas", "admin-summary-note"));
        VBox stateCard = card(states);
        stateCard.getStyleClass().add("admin-card");

        VBox team = new VBox(12, label("Agentes y carga", "section-title"));
        TableView<Registro> agents = table(data.agentWorkload(), "agente", "Agente", "clientes", "Clientes", "propiedades", "Propiedades");
        agents.setPrefHeight(245);
        team.getChildren().addAll(agents, secondary("Ver equipo y usuarios", this::users));
        VBox teamCard = card(team); teamCard.getStyleClass().add("admin-card");
        HBox overview = new HBox(16, typeCard, stateCard, teamCard);
        overview.getStyleClass().add("admin-overview");
        HBox.setHgrow(typeCard, Priority.ALWAYS); HBox.setHgrow(stateCard, Priority.ALWAYS); HBox.setHgrow(teamCard, Priority.ALWAYS);
        typeCard.setPrefWidth(290); stateCard.setPrefWidth(330); teamCard.setPrefWidth(360);
        content.getChildren().add(overview);

        TableView<Registro> latestRequests = table(data.latestRequests(),
                "id_publicacion", "ID", "titulo", "Propiedad", "solicitante", "Solicitante", "fecha", "Fecha", "estado", "Estado");
        TableView<Registro> latestProperties = table(data.latestProperties(),
                "id_propiedad", "ID", "titulo", "Propiedad", "localidad", "Ubicación", "verificacion", "Publicación", "comercial", "Estado comercial");
        VBox requestCard = card(label("Solicitudes de publicación", "section-title"), latestRequests,
                row(button("Ver propiedad", () -> propertyDetails(selected(latestRequests).id("id_propiedad"))),
                    secondary("Gestionar solicitudes", this::requests)));
        long requestTotal = data.requestStates().values().stream().mapToLong(Long::longValue).sum();
        VBox requestStatus = adminBars("Solicitudes por estado", data.requestStates(), requestTotal);
        requestStatus.setPrefWidth(300);
        requestStatus.getStyleClass().add("admin-card");
        requestCard.setPrefWidth(710);
        HBox requestRow = new HBox(16, requestCard, requestStatus);
        HBox.setHgrow(requestCard, Priority.ALWAYS);
        content.getChildren().add(requestRow);

        List<Registro> recentOwnerRequests = service.ownerRequests().stream().limit(6).toList();
        TableView<Registro> ownerRequests = table(recentOwnerRequests, "id_solicitud", "ID",
                "titulo", "Propiedad propuesta", "cliente_nombre", "Propietario", "estado_nombre", "Estado",
                "agente", "Agente");
        content.getChildren().add(card(label("Propuestas de propietarios", "section-title"), ownerRequests,
                row(button("Ver propuesta", () -> ownerRequestDetails(selected(ownerRequests).id("id_solicitud"))),
                        secondary("Gestionar propuestas", this::requests))));

        VBox propertiesCard = card(label("Propiedades recientes", "section-title"), latestProperties,
                row(button("Ver detalle", () -> propertyDetails(selected(latestProperties).id("id_propiedad"))),
                    secondary("Ver todas", this::propertyList)));
        propertiesCard.getStyleClass().add("admin-card");
        content.getChildren().add(propertiesCard);

        TableView<Registro> activity = table(data.recentActivity(),
                "fecha", "Fecha", "usuario", "Usuario", "accion", "Acción", "entidad", "Área", "detalle", "Detalle");
        VBox activityCard = card(label("Actividad reciente", "section-title"), activity,
                secondary("Ver reportes", this::reports));
        activityCard.getStyleClass().add("admin-card");
        content.getChildren().add(activityCard);
    }

    private VBox adminMetric(String icon, String name, long value, String detail, String tone) {
        HBox header = new HBox(10, label(icon, "admin-metric-icon"), label(name, "admin-metric-name"));
        header.setAlignment(Pos.CENTER_LEFT);
        VBox metric = new VBox(8, header, label(Long.toString(value), "admin-metric-value"), label(detail, "admin-metric-detail"));
        metric.getStyleClass().addAll("admin-metric", "metric-" + tone);
        metric.setPrefWidth(190);
        return metric;
    }

    private VBox adminBars(String title, Map<String, Long> values, long total) {
        VBox box = new VBox(13, label(title, "section-title"));
        if (values.isEmpty()) box.getChildren().add(label("Todavía no hay solicitudes.", "admin-summary-note"));
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            HBox row = new HBox(9);
            row.setAlignment(Pos.CENTER_LEFT);
            Label name = label(entry.getKey(), "admin-state-name"); name.setMinWidth(95);
            ProgressBar bar = new ProgressBar(total == 0 ? 0 : (double) entry.getValue() / total);
            bar.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(bar, Priority.ALWAYS);
            row.getChildren().addAll(name, bar, label(Long.toString(entry.getValue()), "admin-state-count"));
            box.getChildren().add(row);
        }
        return box;
    }

    private static VBox stat(String name, long value) {
        VBox v = card(label(name, "subtitle"), label("" + value, "stat"));
        v.setPrefWidth(203);
        return v;
    }

    private void propertyList() {
        page(
                service.role() == 3 ? "Mis propiedades" : "Propiedades",
                "Administrá las fichas, sus estados y las solicitudes de publicación.");
        List<Registro> all = service.allProperties();
        TableView<Registro> t =
                table(
                        all,
                        "id_propiedad",
                        "ID",
                        "titulo",
                        "Título",
                        "localidad",
                        "Localidad",
                        "propietario",
                        "Propietario",
                        "agente_responsable",
                        "Agente responsable",
                        "verificacion",
                        "Publicación",
                        "comercial",
                        "Estado comercial");
        TextField q = new TextField();
        q.setPromptText("Buscar por título o localidad");
        CheckBox mine = new CheckBox("Creadas por mí");
        Runnable filter =
                () ->
                        t.setItems(
                                FXCollections.observableArrayList(
                                        all.stream()
                                                .filter(
                                                        r ->
                                                                (r.text("titulo")
                                                                                + r.text(
                                                                                        "localidad"))
                                                                        .toLowerCase(Locale.ROOT)
                                                                        .contains(
                                                                                q.getText()
                                                                                        .toLowerCase(
                                                                                                Locale
                                                                                                        .ROOT)))
                                                .filter(
                                                        r ->
                                                                !mine.isSelected()
                                                                        || r.id("id_agente_creador")
                                                                                == service.current()
                                                                                        .id(
                                                                                                "id_usuario"))
                                                .toList()));
        q.textProperty().addListener((o, a, b) -> filter.run());
        mine.setOnAction(e -> filter.run());
        HBox top = row(q, button("+ Nueva propiedad", () -> propertyForm(null)));
        if (service.role() != 3) top.getChildren().add(mine);
        FlowPane actions =
                flow(
                        secondary("Ver archivos", () -> propertyFiles(selected(t).id("id_propiedad"))),
                        secondary("Editar", () -> propertyForm(selected(t))),
                        secondary(
                                "Enviar a revisión",
                                () -> {
                                    service.submit(selected(t).id("id_propiedad"));
                                    propertyList();
                                }));
        if (service.role() != 3)
            actions.getChildren()
                    .addAll(
                            secondary("Cambiar estado", () -> stateForm(selected(t))),
                            secondary("Registrar cierre", () -> closeForm(selected(t))));
        content.getChildren().addAll(top, card(t, actions));
    }

    private void propertyFiles(int id) {
        currentPageReload = () -> propertyFiles(id);
        Registro property = service.property(id);
        page("Archivos · " + property.text("titulo"),
                "Fotos, videos y documentación asociados a esta propiedad.");
        content.getChildren().add(secondary("← Volver a propiedades", this::propertyList));

        List<Registro> media = service.attachments(id, false);
        VBox mediaBox = card(label("Fotos y videos", "section-title"));
        if (media.isEmpty()) {
            mediaBox.getChildren().add(label("Esta propiedad todavía no tiene fotos ni videos.", "subtitle"));
        } else {
            double width = Math.min(900, Math.max(320, stage.getScene().getWidth() - 330));
            mediaBox.getChildren().add(propertyGallery(property, width, width * 370 / 650));
        }
        content.getChildren().add(mediaBox);

        if (service.canViewPrivateProperty(id)) {
            VBox documents = card(
                    label("Planos, escrituras y otros documentos", "section-title"),
                    label("Archivos privados: solo visibles para el propietario, administración y el agente responsable.", "subtitle"));
            addFileButtons(documents, id, true);
            content.getChildren().add(documents);
        }
    }

    private void propertyForm(Registro original) {
        if (service.role() == 3 && original == null) {
            ownerRequestForm();
            return;
        }
        Registro p = original == null ? new Registro() : original;
        boolean edit = original != null;
        page(
                edit ? "Editar propiedad #" + p.id("id_propiedad") : "Nueva propiedad",
                "Completá los datos y agregá fotos o videos antes de enviar. Las modificaciones"
                        + " de una propiedad publicada requieren una nueva aprobación.");
        Formulario f = new Formulario();
        f.text("titulo", "Título *", p.text("titulo"));
        f.rows(
                "id_tipo_propiedad",
                "Tipo de propiedad *",
                service.lookup("tipos_propiedad"),
                p.id("id_tipo_propiedad"),
                false);
        f.text("direccion", "Dirección *", p.text("direccion"));
        f.text("localidad", "Localidad *", p.text("localidad"));
        if (service.role() != 3)
            f.rows(
                    "id_propietario",
                    "Propietario *",
                    service.clients(),
                    p.id("id_propietario"),
                    false);
        f.rows(
                "id_edificio",
                "Edificio / complejo",
                service.lookup("edificios"),
                p.id("id_edificio"),
                true);
        f.text("numero_unidad", "Número de unidad (si hay edificio)", p.text("numero_unidad"));
        f.text("piso", "Piso", p.text("piso"));
        f.text("departamento_numero", "Departamento", p.text("departamento_numero"));
        String[][] numbers = {
            {"superficie_total_m2", "Superficie total m² *", ""},
            {"superficie_cubierta_m2", "Superficie cubierta m²", "0"},
            {"ambientes", "Ambientes", "0"},
            {"dormitorios", "Dormitorios", "0"},
            {"banos", "Baños", "0"},
            {"antiguedad_anos", "Antigüedad (años)", "0"},
            {"capacidad", "Capacidad (personas)", "1"},
            {"expensas", "Expensas (ARS)", "0"}
        };
        for (String[] n : numbers) f.text(n[0], n[1], edit ? p.text(n[0]) : n[2]);
        f.check("apto_profesional", "Apto profesional", p.flag("apto_profesional"));
        f.area("descripcion", "Descripción *", p.text("descripcion"));
        Set<Integer> chosen = new HashSet<>();
        if (edit) service.features(p.id("id_propiedad")).forEach(r -> chosen.add(r.id("id")));
        Map<Integer, CheckBox> features = featureChecks(chosen);
        Integer[] savedPropertyId = {edit ? p.id("id_propiedad") : null};
        List<Path> pendingMedia = new ArrayList<>();
        int[] savedMediaCount = {edit ? service.attachments(p.id("id_propiedad"), false).size() : 0};
        Label existingMedia = label("Archivos multimedia vinculados: " + savedMediaCount[0], "subtitle");
        Label selectedMedia = label("Todavía no seleccionaste fotos ni videos.", "subtitle");
        selectedMedia.setWrapText(true);
        Runnable refreshMedia = () -> {
            existingMedia.setText("Archivos multimedia vinculados: " + savedMediaCount[0]);
            selectedMedia.setText(pendingMedia.isEmpty()
                    ? "Todavía no seleccionaste fotos ni videos."
                    : "Pendientes de cargar: " + pendingMedia.stream()
                            .map(path -> path.getFileName().toString())
                            .collect(java.util.stream.Collectors.joining(", ")));
        };
        VBox mediaBox = card(
                label("Fotos y videos de la propiedad", "section-title"),
                label("Podés seleccionar varias imágenes JPG, JPEG o PNG y videos MP4 (H.264). "
                        + "Cada archivo puede pesar hasta 50 MB. Se cargarán antes de enviar la "
                        + "solicitud para que el equipo pueda revisarlos.", "subtitle"),
                existingMedia,
                selectedMedia,
                secondary("Seleccionar fotos o videos", () -> {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Seleccionar fotos o videos de la propiedad");
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                            "Imágenes y videos", "*.jpg", "*.jpeg", "*.png", "*.mp4"));
                    List<java.io.File> files = chooser.showOpenMultipleDialog(stage);
                    if (files == null) return;
                    for (java.io.File file : files) {
                        Path path = file.toPath();
                        if (!pendingMedia.contains(path)) pendingMedia.add(path);
                    }
                    refreshMedia.run();
                }));
        Map<Path, String> pendingDocuments = new LinkedHashMap<>();
        List<ServicioInmobiliaria.PropertyTax> pendingTaxes = new ArrayList<>();
        VBox documentsBox = pendingDocumentsPanel(pendingDocuments);
        VBox taxesBox = pendingTaxesPanel(pendingTaxes);
        if (edit) {
            documentsBox.getChildren().add(label("Documentos ya guardados · tocá un archivo para abrirlo", "section-title"));
            addFileButtons(documentsBox, p.id("id_propiedad"), true);
            for (Registro tax : service.propertyTaxes(p.id("id_propiedad"))) taxesBox.getChildren().add(taxSummary(tax));
        }
        VBox offersBox =
                card(
                        label("Operaciones y precios", "section-title"),
                        label(
                                "Activá venta, alquiler o ambas. Cada oferta tiene su propio precio"
                                        + " y moneda.",
                                "subtitle"));
        Map<Integer, CheckBox> enabled = new LinkedHashMap<>();
        Map<Integer, TextField> prices = new HashMap<>();
        Map<Integer, TextField> minimumPrices = new HashMap<>();
        Map<Integer, ComboBox<String>> currencies = new HashMap<>();
        List<Registro> existing = edit ? service.offers(p.id("id_propiedad")) : List.of();
        for (Registro type : service.lookup("tipos_transaccion")) {
            int id = type.id("id");
            Registro offer =
                    existing.stream()
                            .filter(o -> o.id("id_tipo_transaccion") == id)
                            .findFirst()
                            .orElse(new Registro());
            CheckBox on = new CheckBox(type.text("etiqueta"));
            on.setPrefWidth(150);
            on.setSelected(!offer.isEmpty());
            TextField price = new TextField(offer.text("precio"));
            price.setPromptText("Precio");
            price.setPrefWidth(180);
            TextField minimum = new TextField(id == 1 ? offer.text("precio_minimo") : "");
            minimum.setPromptText("Mínimo interno de venta");
            minimum.setPrefWidth(190);
            ComboBox<String> currency =
                    new ComboBox<>(FXCollections.observableArrayList("USD", "ARS"));
            currency.setValue(offer.isEmpty() ? "USD" : offer.text("moneda"));
            enabled.put(id, on);
            prices.put(id, price);
            minimumPrices.put(id, minimum);
            currencies.put(id, currency);
            showOnlyWhenSelected(on, price, currency);
            if (id == 1 && service.role() != 3) showOnlyWhenSelected(on, minimum);
            offersBox.getChildren().add(id == 1 && service.role() != 3
                    ? row(on, price, currency, minimum) : row(on, price, currency));
        }
        Consumer<Boolean> save =
                submit -> {
                    List<ServicioInmobiliaria.Offer> offers = new ArrayList<>();
                    enabled.forEach(
                            (id, c) -> {
                                if (c.isSelected())
                                    offers.add(
                                            new ServicioInmobiliaria.Offer(
                                                    id,
                                                    money(prices.get(id).getText()),
                                                    currencies.get(id).getValue(),
                                                    id == 1 && !minimumPrices.get(id).getText().isBlank()
                                                            ? money(minimumPrices.get(id).getText()) : null));
                            });
                    int id = service.saveProperty(
                            savedPropertyId[0], f.values(), offers, checked(features), false);
                    savedPropertyId[0] = id;
                    while (!pendingMedia.isEmpty()) {
                        Path path = pendingMedia.get(0);
                        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        String mediaType = filename.endsWith(".mp4")
                                ? "Video" : "Imagen";
                        try {
                            service.attach(id, path, mediaType);
                        } catch (Exception e) {
                            throw new IllegalArgumentException(
                                    "No se pudo cargar " + path.getFileName() + ": " + e.getMessage(), e);
                        }
                        pendingMedia.remove(0);
                        savedMediaCount[0]++;
                        refreshMedia.run();
                    }
                    while (!pendingDocuments.isEmpty()) {
                        var document = pendingDocuments.entrySet().iterator().next();
                        try { service.attach(id, document.getKey(), document.getValue()); }
                        catch (Exception e) { throw new IllegalArgumentException("No se pudo cargar " + document.getKey().getFileName() + ": " + e.getMessage(), e); }
                        pendingDocuments.remove(document.getKey());
                    }
                    while (!pendingTaxes.isEmpty()) {
                        service.savePropertyTax(id, pendingTaxes.getFirst());
                        pendingTaxes.removeFirst();
                    }
                    if (submit) {
                        if (service.role() == 1) service.publishDirectly(id);
                        else service.submit(id);
                    }
                    propertyDetails(id);
                };
        List<Node> propertyActions = new ArrayList<>();
        if (service.role() == 1) {
            propertyActions.add(button("Guardar y publicar", () -> save.accept(true)));
        } else {
            propertyActions.add(secondary("Guardar borrador", () -> save.accept(false)));
            propertyActions.add(button("Guardar y enviar a revisión", () -> save.accept(true)));
        }
        propertyActions.add(secondary("Cancelar", this::propertyList));
        content.getChildren()
                .addAll(
                        card(f),
                        mediaBox,
                        documentsBox,
                        taxesBox,
                        offersBox,
                        card(
                                label("Características adicionales", "section-title"),
                                flow(features.values().toArray(Node[]::new))),
                        flow(propertyActions.toArray(Node[]::new)));
    }

    private void ownerRequestForm() {
        page("Solicitar publicación", "Enviá los datos del inmueble para que un agente lo revise y administración decida su publicación.");
        Formulario f = new Formulario();
        f.text("titulo", "Título *", "");
        f.rows("id_tipo_propiedad", "Tipo de propiedad *", service.lookup("tipos_propiedad"), 0, false);
        f.text("direccion", "Dirección *", "");
        f.text("localidad", "Localidad *", "");
        f.text("superficie_total_m2", "Superficie total m² *", "");
        f.text("ambientes", "Ambientes", "0");
        f.text("dormitorios", "Dormitorios", "0");
        f.text("banos", "Baños", "0");
        f.text("antiguedad_anos", "Antigüedad (años)", "0");
        f.area("descripcion", "Descripción *", "");
        VBox offersBox = card(label("Operaciones y precios propuestos", "section-title"),
                label("Podés proponer venta, alquiler o ambas operaciones. Cada una tiene su precio y moneda.", "subtitle"));
        Map<Integer, CheckBox> enabled = new LinkedHashMap<>();
        Map<Integer, TextField> prices = new HashMap<>();
        Map<Integer, TextField> minimumPrices = new HashMap<>();
        Map<Integer, ComboBox<String>> currencies = new HashMap<>();
        for (Registro type : service.lookup("tipos_transaccion")) {
            int typeId = type.id("id");
            CheckBox on = new CheckBox(type.text("etiqueta"));
            TextField price = new TextField();
            price.setPromptText("Precio");
            TextField minimum = new TextField();
            minimum.setPromptText("Mínimo aceptado por el propietario");
            ComboBox<String> currency = new ComboBox<>(FXCollections.observableArrayList("USD", "ARS"));
            currency.setValue("USD");
            enabled.put(typeId, on);
            prices.put(typeId, price);
            minimumPrices.put(typeId, minimum);
            currencies.put(typeId, currency);
            showOnlyWhenSelected(on, price, currency);
            if (typeId == 1) {
                showOnlyWhenSelected(on, minimum);
                offersBox.getChildren().add(row(on, price, currency, minimum));
            } else {
                offersBox.getChildren().add(row(on, price, currency));
            }
        }
        Map<Integer, CheckBox> features = featureChecks(Set.of());
        Map<Path, String> pendingDocuments = new LinkedHashMap<>();
        Runnable[] refreshValidationRef = {() -> {}};
        VBox documents = pendingDocumentsPanel(pendingDocuments, () -> refreshValidationRef[0].run());
        List<Path> pendingMedia = new ArrayList<>();
        Label mediaStatus = label("Archivos seleccionados: 0/8.", "subtitle");
        mediaStatus.setWrapText(true);
        VBox media = card(label("Fotos y videos", "section-title"),
                label("Los archivos se guardarán en la carpeta local de la aplicación y quedarán asociados a la solicitud.", "subtitle"),
                secondary("Seleccionar fotos o videos", () -> {
                    FileChooser chooser = new FileChooser();
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes y videos", "*.jpg", "*.jpeg", "*.png", "*.webp", "*.mp4"));
                    List<java.io.File> files = chooser.showOpenMultipleDialog(stage);
                    if (files != null) for (java.io.File file : files) {
                        Path path = file.toPath();
                        if (pendingMedia.contains(path)) continue;
                        if (pendingMedia.size() >= 8) {
                            info("Podés adjuntar hasta 8 fotos o videos por solicitud.");
                            break;
                        }
                        try {
                            if (Files.size(path) > 2L * 1024 * 1024) {
                                info("Cada foto o video puede pesar hasta 2 MB: " + file.getName());
                                continue;
                            }
                        } catch (Exception e) {
                            info("No se pudo leer el archivo " + file.getName());
                            continue;
                        }
                        pendingMedia.add(path);
                    }
                    mediaStatus.setText("Archivos seleccionados: " + pendingMedia.size() + "/8"
                            + (pendingMedia.isEmpty() ? "" : " · " + pendingMedia.stream()
                                    .map(path -> path.getFileName().toString())
                                    .collect(java.util.stream.Collectors.joining(", "))));
                }), mediaStatus);
        CheckBox missingPlans = new CheckBox("Todavía no tengo los planos");
        CheckBox missingDeed = new CheckBox("Todavía no tengo la escritura");
        Label validationStatus = label("Completá los campos marcados con * y elegí al menos una operación.", "subtitle");
        validationStatus.setWrapText(true);
        Runnable refreshValidation = () -> {
            List<String> issues = ownerRequestMissingFields(f.values(), enabled, prices,
                    minimumPrices, pendingDocuments, missingPlans.isSelected(), missingDeed.isSelected());
            validationStatus.setText(issues.isEmpty()
                    ? "Los datos obligatorios están completos."
                    : "Para enviar, revisá: " + String.join(" · ", issues));
        };
        refreshValidationRef[0] = refreshValidation;
        for (String key : List.of("titulo", "direccion", "localidad", "superficie_total_m2",
                "ambientes", "dormitorios", "banos", "antiguedad_anos", "descripcion")) {
            if (f.control(key) instanceof TextInputControl input)
                input.textProperty().addListener((o, old, value) -> refreshValidation.run());
        }
        for (CheckBox option : enabled.values())
            option.selectedProperty().addListener((o, old, value) -> refreshValidation.run());
        prices.values().forEach(input -> input.textProperty().addListener((o, old, value) -> refreshValidation.run()));
        minimumPrices.values().forEach(input -> input.textProperty().addListener((o, old, value) -> refreshValidation.run()));
        missingPlans.selectedProperty().addListener((o, old, value) -> refreshValidation.run());
        missingDeed.selectedProperty().addListener((o, old, value) -> refreshValidation.run());
        Formulario documentation = new Formulario(1);
        documentation.area("observaciones_documentacion", "Aclaración sobre documentación pendiente", "");
        List<ServicioInmobiliaria.PropertyTax> taxes = new ArrayList<>();
        VBox taxPanel = pendingTaxesPanel(taxes);
        refreshValidation.run();
        content.getChildren().addAll(card(f), offersBox,
                card(label("Características", "section-title"), flow(features.values().toArray(Node[]::new))),
                media, documents,
                card(label("Planos y escritura", "section-title"),
                        label("Adjuntá ambos documentos o indicá cuál todavía no tenés.", "subtitle"),
                        missingPlans, missingDeed, documentation),
                validationStatus, taxPanel,
                flow(button("Enviar solicitud", () -> {
                    List<String> currentIssues = ownerRequestMissingFields(f.values(), enabled, prices,
                            minimumPrices, pendingDocuments, missingPlans.isSelected(), missingDeed.isSelected());
                    validationStatus.setText(currentIssues.isEmpty()
                            ? "Los datos obligatorios están completos."
                            : "Para enviar, revisá: " + String.join(" · ", currentIssues));
                    if (!currentIssues.isEmpty())
                        throw new IllegalArgumentException("Antes de enviar, completá o revisá:\n• "
                                + String.join("\n• ", currentIssues));
                    List<String> missing = new ArrayList<>();
                    Map<String, String> values = f.values();
                    Map<String, String> requiredFields = new LinkedHashMap<>();
                    requiredFields.put("titulo", "Título");
                    requiredFields.put("id_tipo_propiedad", "Tipo de propiedad");
                    requiredFields.put("direccion", "Dirección");
                    requiredFields.put("localidad", "Localidad");
                    requiredFields.put("superficie_total_m2", "Superficie total");
                    requiredFields.put("descripcion", "Descripción");
                    requiredFields.forEach((key, name) -> {
                        if (values.getOrDefault(key, "").isBlank()) missing.add(name);
                    });

                    boolean plansAttached = pendingDocuments.values().contains("Plano");
                    boolean deedAttached = pendingDocuments.values().contains("Escritura");
                    if (!plansAttached && !missingPlans.isSelected()) missing.add("Planos: adjuntá el archivo o marcá que todavía no los tenés");
                    if (!deedAttached && !missingDeed.isSelected()) missing.add("Escritura: adjuntá el archivo o marcá que todavía no la tenés");
                    List<ServicioInmobiliaria.Offer> proposed = new ArrayList<>();
                    enabled.forEach((id, selected) -> {
                        if (!selected.isSelected()) return;
                        String operation = selected.getText();
                        String rawPrice = prices.get(id).getText().trim();
                        if (rawPrice.isBlank()) {
                            missing.add("Precio de " + operation);
                            return;
                        }
                        try {
                            BigDecimal amount = money(rawPrice);
                            String rawMinimum = minimumPrices.get(id).getText().trim();
                            BigDecimal minimum = null;
                            if (id == 1 && rawMinimum.isBlank()) {
                                missing.add("Mínimo aceptado por el propietario para la venta");
                                return;
                            }
                            if (id == 1) {
                                minimum = money(rawMinimum);
                                if (minimum.compareTo(amount) > 0) {
                                    missing.add("El mínimo aceptado para la venta no puede superar el precio publicado");
                                    return;
                                }
                            }
                            proposed.add(new ServicioInmobiliaria.Offer(id, amount,
                                    currencies.get(id).getValue(), minimum));
                        } catch (NumberFormatException invalidPrice) {
                            missing.add("Precio de " + operation + " (ingresá un número válido)");
                        }
                    });
                    if (enabled.values().stream().noneMatch(CheckBox::isSelected))
                        missing.add("Operación: elegí venta, alquiler o ambas");
                    if (!missing.isEmpty())
                        throw new IllegalArgumentException("Antes de enviar, completá o revisá:\n• "
                                + String.join("\n• ", missing));
                    int id = service.createOwnerRequest(f.values(), proposed, checked(features),
                            missingPlans.isSelected(), missingDeed.isSelected(),
                            documentation.value("observaciones_documentacion"));
                    try {
                        for (Path path : pendingMedia)
                            service.attachOwnerRequest(id, path, path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp4") ? "Video" : "Imagen");
                        for (Map.Entry<Path, String> doc : pendingDocuments.entrySet())
                            service.attachOwnerRequest(id, doc.getKey(), doc.getValue());
                        for (ServicioInmobiliaria.PropertyTax tax : taxes) service.saveOwnerRequestTax(id, tax);
                    } catch (Exception error) {
                        throw new IllegalArgumentException("La solicitud quedó creada, pero no se pudo adjuntar todo: " + error.getMessage(), error);
                    }
                    ownerRequestsPage();
                    info("La solicitud quedó enviada para revisión.");
                }), secondary("Cancelar", this::propertyList)));
    }

    private List<String> ownerRequestMissingFields(Map<String, String> values,
            Map<Integer, CheckBox> enabled, Map<Integer, TextField> prices,
            Map<Integer, TextField> minimumPrices, Map<Path, String> pendingDocuments,
            boolean missingPlans, boolean missingDeed) {
        List<String> missing = new ArrayList<>();
        Map<String, String> required = new LinkedHashMap<>();
        required.put("titulo", "Título");
        required.put("id_tipo_propiedad", "Tipo de propiedad");
        required.put("direccion", "Dirección");
        required.put("localidad", "Localidad");
        required.put("superficie_total_m2", "Superficie total");
        required.put("descripcion", "Descripción");
        required.forEach((key, name) -> {
            if (values.getOrDefault(key, "").isBlank()) missing.add(name);
        });
        String rawSurface = values.getOrDefault("superficie_total_m2", "").trim();
        if (!rawSurface.isBlank()) {
            try {
                BigDecimal surface = money(rawSurface);
                if (surface.signum() <= 0 || surface.compareTo(new BigDecimal("999999.99")) > 0)
                    missing.add("Superficie total fuera del rango permitido");
            } catch (NumberFormatException invalidNumber) {
                missing.add("Superficie total debe ser numérica");
            }
        }
        for (String key : List.of("ambientes", "dormitorios", "banos", "antiguedad_anos")) {
            String value = values.getOrDefault(key, "").trim();
            if (value.isBlank()) continue;
            try {
                int number = Integer.parseInt(value);
                if (number < 0 || number > 1000) missing.add(key + " debe estar entre 0 y 1000");
            } catch (NumberFormatException invalidNumber) {
                missing.add(key + " debe ser un número entero");
            }
        }
        if (!pendingDocuments.values().contains("Plano") && !missingPlans)
            missing.add("Adjuntar planos o marcar que no se tienen");
        if (!pendingDocuments.values().contains("Escritura") && !missingDeed)
            missing.add("Adjuntar escritura o marcar que no se tiene");
        boolean hasOperation = false;
        for (Map.Entry<Integer, CheckBox> entry : enabled.entrySet()) {
            if (!entry.getValue().isSelected()) continue;
            hasOperation = true;
            String operation = entry.getValue().getText();
            String rawPrice = prices.get(entry.getKey()).getText().trim();
            if (rawPrice.isBlank()) {
                missing.add("Precio de " + operation);
                continue;
            }
            try {
                BigDecimal amount = money(rawPrice);
                if (amount.signum() <= 0) missing.add("Precio de " + operation + " debe ser mayor a cero");
                if (amount.scale() > 2 || amount.compareTo(new BigDecimal("9999999999.99")) > 0)
                    missing.add("Precio de " + operation + " admite hasta 2 decimales y hasta 9.999.999.999,99");
                if (entry.getKey() == 1) {
                    String rawMinimum = minimumPrices.get(entry.getKey()).getText().trim();
                    if (rawMinimum.isBlank()) missing.add("Mínimo aceptado para la venta");
                    else {
                        BigDecimal minimum = money(rawMinimum);
                        if (minimum.signum() <= 0 || minimum.scale() > 2
                                || minimum.compareTo(new BigDecimal("9999999999.99")) > 0
                                || minimum.compareTo(amount) > 0)
                            missing.add("Revisar el mínimo aceptado para la venta");
                    }
                }
            } catch (NumberFormatException invalidNumber) {
                missing.add("Ingresar un precio válido para " + operation);
            }
        }
        if (!hasOperation) missing.add("Elegir venta, alquiler o alquiler temporario");
        return missing;
    }

    private void stateForm(Registro p) {
        Formulario f = new Formulario();
        f.rows(
                "estado",
                "Nuevo estado",
                service.lookup("estados_comerciales").stream()
                        .filter(r -> Set.of(1, 2, 5, 6).contains(r.id("id")))
                        .toList(),
                p.id("id_estado_comercial"),
                false);
        f.area("motivo", "Motivo del cambio", "");
        dialog(
                "Estado de " + p.text("titulo"),
                card(
                        f,
                        label(
                                "Al reactivar una propiedad cerrada o fuera de mercado, vuelve a"
                                        + " borrador y requiere aprobación.",
                                "subtitle")),
                "Guardar cambio",
                () -> {
                    service.changeState(
                            p.id("id_propiedad"), number(f.value("estado")), f.value("motivo"));
                    propertyList();
                });
    }

    private void closeForm(Registro p) {
        Formulario f = new Formulario();
        f.rows("client", "Cliente comprador / inquilino", service.clients(), 0, false);
        List<Registro> offers = new ArrayList<>();
        for (Registro o : service.offers(p.id("id_propiedad"))) {
            Registro r = new Registro();
            r.put("id", o.id("id_tipo_transaccion"));
            r.put("etiqueta", o.text("operacion") + " · " + o.text("moneda"));
            r.put("precio", o.get("precio"));
            offers.add(r);
        }
        ComboBox<Registro> operation = f.rows("operation", "Operación / moneda", offers, 0, false);
        TextField finalPrice =
                f.text(
                        "price",
                        "Precio final (moneda de la oferta)",
                        operation.getValue() == null ? "" : operation.getValue().text("precio"));
        operation
                .valueProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (after != null) finalPrice.setText(after.text("precio"));
                        });
        f.text("commission", "Comisión total (misma moneda)", "0");
        dialog(
                "Registrar cierre",
                f,
                "Confirmar cierre",
                () -> {
                    service.close(
                            p.id("id_propiedad"),
                            number(f.value("client")),
                            number(f.value("operation")),
                            money(f.value("price")),
                            money(f.value("commission")));
                    operations();
                });
    }

    private void requests() {
        if (service.role() == 3) {
            ownerRequestsPage();
            return;
        }
        page(
                "Solicitudes de publicación",
                "Gestioná propuestas de propietarios y revisiones de fichas existentes. Los rechazos incluyen el motivo para corregir la"
                        + " ficha.");
        TableView<Registro> proposals = table(service.ownerRequests(), "id_solicitud", "ID",
                "titulo", "Propiedad propuesta", "cliente_nombre", "Propietario", "tipo", "Tipo",
                "operacion", "Operación", "estado_nombre", "Estado", "agente", "Agente");
        FlowPane proposalActions = flow(button("Ver solicitud", () -> ownerRequestDetails(selected(proposals).id("id_solicitud"))));
        if (service.role() == 2) proposalActions.getChildren().add(secondary("Tomar sin asignar", () -> {
            service.claimOwnerRequest(selected(proposals).id("id_solicitud"));
            requests();
        }));
        if (service.role() == 1) proposalActions.getChildren().addAll(
                secondary("Asignar agente", () -> assignOwnerRequest(selected(proposals).id("id_solicitud"))),
                button("Aprobar y publicar", () -> reviewOwnerRequest(selected(proposals).id("id_solicitud"), true)),
                secondary("Rechazar", () -> reviewOwnerRequest(selected(proposals).id("id_solicitud"), false)));
        content.getChildren().add(card(label("Solicitudes de propietarios", "section-title"), proposals, proposalActions));
        TableView<Registro> t =
                table(
                        service.requests(),
                        "id_publicacion",
                        "ID",
                        "titulo",
                        "Propiedad",
                        "solicitante",
                        "Solicitante",
                        "estado",
                        "Estado",
                        "observaciones",
                        "Observaciones",
                        "fecha_solicitud",
                        "Enviada");
        FlowPane actions =
                flow(
                        button(
                                "Ver propiedad",
                                () -> propertyDetails(selected(t).id("id_propiedad"))));
        if (service.role() == 1) {
            actions.getChildren()
                    .addAll(
                            secondary("Checklist documental", () -> publicationChecklist(selected(t).id("id_propiedad"))),
                            secondary("Aprobar", () -> reviewForm(selected(t), true)),
                            secondary("Rechazar", () -> reviewForm(selected(t), false)));
        }
        content.getChildren().add(card(t, actions));
    }

    private void ownerRequestsPage() {
        page("Mis solicitudes", "Seguí las propuestas de tus propiedades y revisá las observaciones de administración.");
        TableView<Registro> requests = table(service.ownerRequests(), "id_solicitud", "ID", "titulo", "Propiedad",
                "tipo", "Tipo", "operacion", "Operación", "estado_nombre", "Estado", "fecha_solicitud", "Enviada", "motivo_rechazo", "Observaciones");
        content.getChildren().addAll(button("+ Nueva solicitud de publicación", this::ownerRequestForm),
                card(requests, button("Ver solicitud", () -> ownerRequestDetails(selected(requests).id("id_solicitud")))));
    }

    private void ownerRequestDetails(int id) {
        Registro request = service.ownerRequest(id);
        page("Solicitud #" + id + " · " + request.text("titulo"),
                request.text("cliente_nombre") + " " + request.text("cliente_apellido") + " · "
                        + request.text("cliente_email") + " · " + request.text("estado_nombre"));
        content.getChildren().add(card(label("Datos de la propiedad", "section-title"),
                label(request.text("direccion") + " · " + request.text("localidad"), "subtitle"),
                label("Tipo: " + request.text("tipo") + " · Superficie: " + request.text("superficie_total_m2")
                        + " m² · Ambientes: " + request.text("ambientes") + " · Dormitorios: " + request.text("dormitorios")
                        + " · Baños: " + request.text("banos") + " · Antigüedad: " + request.text("antiguedad_anos"), "subtitle"),
                label(request.text("descripcion"), "subtitle"),
                label("Operaciones y precios", "section-title"),
                table(service.ownerRequestOffers(id), "operacion", "Operación", "precio", "Precio", "moneda", "Moneda"),
                label("Características", "section-title"),
                table(service.ownerRequestFeatures(id), "etiqueta", "Característica"),
                label("Documentación pendiente: planos=" + (request.flag("faltan_planos") ? "sí" : "no")
                        + " · escritura=" + (request.flag("falta_escritura") ? "sí" : "no"), "subtitle"),
                label(request.text("observaciones_documentacion"), "subtitle")));
        TableView<Registro> media = table(service.ownerRequestMedia(id), "tipo", "Tipo", "archivo", "Archivo");
        content.getChildren().add(card(label("Fotos y videos", "section-title"), media,
                secondary("Abrir archivo seleccionado", () -> openOwnerRequestFile(id, selected(media).id("id"), false))));
        TableView<Registro> documents = table(service.ownerRequestDocuments(id), "tipo", "Tipo", "nombre", "Archivo", "fecha", "Fecha");
        content.getChildren().add(card(label("Documentos privados", "section-title"), documents,
                secondary("Abrir documento seleccionado", () -> openOwnerRequestFile(id, selected(documents).id("id"), true))));
        List<Registro> taxes = service.ownerRequestTaxes(id);
        content.getChildren().add(card(label("Impuestos", "section-title"), taxes.isEmpty()
                ? label("No se adjuntaron cálculos de impuestos.", "subtitle")
                : flow(taxes.stream().map(this::taxSummary).toArray(Node[]::new))));
        List<Node> actions = new ArrayList<>();
        if (service.role() == 2 && request.get("id_agente") == null)
            actions.add(button("Tomar solicitud", () -> { service.claimOwnerRequest(id); ownerRequestDetails(id); }));
        if (service.role() == 1 && request.id("id_estado_verificacion") == 2)
            actions.addAll(List.of(secondary("Asignar agente", () -> assignOwnerRequest(id)),
                    button("Aprobar y publicar", () -> reviewOwnerRequest(id, true)),
                    secondary("Rechazar", () -> reviewOwnerRequest(id, false))));
        actions.add(secondary("Volver", service.role() == 3 ? this::ownerRequestsPage : this::requests));
        content.getChildren().add(flow(actions.toArray(Node[]::new)));
    }

    private void openOwnerRequestFile(int request, int file, boolean privateDocument) {
        try {
            Path path = service.ownerRequestFilePath(request, file, privateDocument);
            if (!Files.isRegularFile(path)) throw new IllegalArgumentException("El archivo local no está disponible.");
            getHostServices().showDocument(path.toUri().toString());
        } catch (Exception e) { throw new IllegalArgumentException(e.getMessage(), e); }
    }

    private void assignOwnerRequest(int id) {
        Formulario f = new Formulario();
        f.rows("agent", "Agente responsable", service.agents(), 0, false);
        dialog("Asignar solicitud #" + id, f, "Guardar", () -> {
            service.assignOwnerRequest(id, number(f.value("agent")));
            requests();
        });
    }

    private void reviewOwnerRequest(int id, boolean approve) {
        if (approve) {
            dialog("Aprobar solicitud #" + id,
                    label("La solicitud se convertirá en una propiedad publicada y sus archivos quedarán asociados a la ficha.", "subtitle"),
                    "Aprobar y publicar", () -> { int property = service.approveOwnerRequest(id); propertyDetails(property); });
        } else {
            Formulario f = new Formulario(1);
            f.area("reason", "Motivo del rechazo *", "");
            dialog("Rechazar solicitud #" + id, f, "Guardar rechazo", () -> {
                service.rejectOwnerRequest(id, f.value("reason"));
                requests();
            });
        }
    }

    private void publicationChecklist(int property) {
        page("Checklist documental · propiedad #" + property,
                "Revisá los requisitos y vinculá la evidencia cargada antes de resolver la solicitud.");
        TableView<Registro> requirements = table(service.checklistPublicacion(property),
                "nombre", "Requisito", "obligatorio", "Obligatorio", "estado", "Estado",
                "documento", "Documento asociado", "observaciones", "Observaciones");
        content.getChildren().add(card(requirements, flow(
                button("Actualizar requisito", () -> updateChecklist(property, selected(requirements))),
                secondary("Volver a solicitudes", this::requests))));
    }

    private void updateChecklist(int property, Registro item) {
        List<Registro> documents = new ArrayList<>();
        for (Registro doc : service.attachments(property, true)) {
            Registro option = new Registro();
            option.put("id", doc.id("id"));
            option.put("etiqueta", doc.text("tipo") + " · " + doc.text("nombre"));
            documents.add(option);
        }
        Formulario f = new Formulario();
        f.choice("estado", "Estado", item.text("estado"), "Pendiente", "Verificado", "Falta documentación");
        f.rows("documento", "Documento adjunto", documents, item.id("id_documento"), true);
        f.area("notes", "Observaciones", item.text("observaciones"));
        dialog("Revisar requisito", f, "Guardar", () -> {
            int doc = number(f.value("documento"));
            service.actualizarChecklistPublicacion(property, item.id("id_requisito"), doc == 0 ? null : doc,
                    f.value("estado"), f.value("notes"));
            publicationChecklist(property);
        });
    }

    private void reviewForm(Registro r, boolean approve) {
        Formulario f = new Formulario();
        f.area("observaciones", approve ? "Observaciones (opcional)" : "Motivo del rechazo *", "");
        int propertyId = r.id("id_propiedad");
        VBox evidence = new VBox(12, label("Documentación de la propiedad", "section-title"));
        addFileButtons(evidence, propertyId, true);
        evidence.getChildren().add(label("Cálculos de impuestos", "section-title"));
        List<Registro> taxes = service.propertyTaxes(propertyId);
        if (taxes.isEmpty()) evidence.getChildren().add(label("Sin cálculos guardados.", "subtitle"));
        for (Registro tax : taxes) evidence.getChildren().add(taxSummary(tax));
        evidence.getChildren().add(f);
        dialog(
                (approve ? "Aprobar" : "Rechazar") + " solicitud #" + r.id("id_publicacion"),
                evidence,
                "Confirmar",
                () -> {
                    service.review(r.id("id_publicacion"), approve, f.value("observaciones"));
                    requests();
                });
    }

    private void clients() {
        page("Clientes", "Ficha única, roles comerciales e historial compartido de atención.");
        TableView<Registro> t =
                table(
                        service.clients(),
                        "id",
                        "ID",
                        "etiqueta",
                        "Cliente",
                        "dni",
                        "Documento",
                        "email",
                        "Correo",
                        "telefono",
                        "Teléfono",
                        "fecha_inicio_relacion",
                        "Inicio relación");
        content.getChildren()
                .addAll(
                        button("+ Registrar cliente", () -> newUser(false)),
                        card(
                                t,
                                flow(
                                        button("Ver ficha", () -> clientDetails(selected(t))),
                                        secondary(
                                                "Registrar contacto",
                                                () -> contactForm(selected(t).id("id"))),
                                        secondary(
                                                "Perfil de búsqueda",
                                                () -> preferences(selected(t).id("id"))),
                                        secondary(
                                                "Intereses",
                                                () -> interests(selected(t).id("id"))),
                                        secondary("Agendar visita", () -> scheduleVisit(selected(t).id("id"))))));
    }

    private void newUser(boolean admin) {
        Formulario f = userForm();
        PasswordField pass = f.password("password", "Contraseña inicial (mínimo 10 caracteres)");
        if (admin)
            f.choice("role", "Rol de acceso", "Cliente", "Cliente", "Agente");
        dialog(
                admin ? "Crear usuario" : "Registrar cliente",
                card(
                        f,
                        label(
                                "El correo y el documento deben ser únicos. El cliente podrá"
                                        + " cambiar su contraseña desde Mi perfil.",
                                "subtitle")),
                "Crear",
                () -> {
                    if (admin) {
                        int role = f.value("role").equals("Agente") ? 2 : 3;
                        service.createStaffUser(f.values(), pass.getText(), role);
                        users();
                    } else {
                        service.registerClient(f.values(), pass.getText());
                        clients();
                    }
                });
    }

    private void clientDetails(Registro client) {
        int id = client.id("id");
        page(
                client.text("etiqueta"),
                client.text("email")
                        + " · "
                        + client.text("telefono")
                        + " · Documento "
                        + client.text("dni"));
        Set<String> roles = new HashSet<>();
        service.clientRoles(id).forEach(r -> roles.add(r.text("tipo_rol")));
        Map<String, CheckBox> checks = new LinkedHashMap<>();
        for (String role :
                List.of("Comprador", "Vendedor", "Propietario", "Inquilino", "Inversor")) {
            CheckBox c = new CheckBox(role);
            c.setSelected(roles.contains(role));
            checks.put(role, c);
        }
        ComboBox<Registro> assignedAgent = null;
        if (service.role() == 1) {
            Registro unassigned = new Registro();
            unassigned.put("id", 0);
            unassigned.put("etiqueta", "Sin asignar");
            List<Registro> options = new ArrayList<>();
            options.add(unassigned);
            options.addAll(service.agents());
            assignedAgent = new ComboBox<>(FXCollections.observableArrayList(options));
            assignedAgent.setMaxWidth(Double.MAX_VALUE);
            int currentAgent = client.get("id_agente_asignado") == null
                    ? 0 : client.id("id_agente_asignado");
            if (currentAgent != 0 && assignedAgent.getItems().stream()
                    .noneMatch(agent -> agent.id("id") == currentAgent)) {
                Registro inactive = new Registro();
                inactive.put("id", currentAgent);
                inactive.put("etiqueta", "Agente actual (inactivo) #" + currentAgent);
                assignedAgent.getItems().add(inactive);
            }
            assignedAgent.getItems().stream()
                    .filter(agent -> agent.id("id") == currentAgent)
                    .findFirst()
                    .ifPresent(assignedAgent.getSelectionModel()::select);
        }
        ComboBox<Registro> agentSelection = assignedAgent;
        TableView<Registro> linkedAgents = table(service.linkedAgents(id),
                "etiqueta", "Agente vinculado por actividad");
        List<Node> clientCard = new ArrayList<>();
        clientCard.add(label("Roles del cliente", "section-title"));
        clientCard.add(flow(checks.values().toArray(Node[]::new)));
        if (agentSelection != null) {
            clientCard.add(label("Agente responsable", "section-title"));
            clientCard.add(agentSelection);
        }
        List<Node> saveActions = new ArrayList<>();
        saveActions.add(button(
                "Guardar ficha",
                () -> {
                    Set<String> chosen = new HashSet<>();
                    checks.forEach((k, c) -> {
                        if (c.isSelected()) chosen.add(k);
                    });
                    Integer newAgent = client.get("id_agente_asignado") == null
                            ? null : client.id("id_agente_asignado");
                    if (agentSelection != null) {
                        Registro selectedAgent = agentSelection.getValue();
                        newAgent = selectedAgent == null || selectedAgent.id("id") == 0
                                ? null : selectedAgent.id("id");
                    }
                    service.saveClient(id, chosen, newAgent);
                    info("Ficha actualizada.");
                    Registro refreshed = service.clients().stream()
                            .filter(row -> row.id("id") == id)
                            .findFirst().orElse(client);
                    clientDetails(refreshed);
                }));
        if (service.role() == 1)
            saveActions.add(secondary("Historial de asignaciones", () -> assignmentHistory(id)));
        clientCard.add(flow(saveActions.toArray(Node[]::new)));
        content.getChildren()
                .add(
                        card(clientCard.toArray(Node[]::new)));
        content.getChildren().add(card(label("Agentes vinculados por actividad", "section-title"),
                linkedAgents));
        content.getChildren()
                .add(
                        card(
                                label("Historial de atención", "section-title"),
                                table(
                                        service.contacts(id),
                                        "fecha",
                                        "Fecha",
                                        "agente",
                                        "Agente",
                                        "tipo",
                                        "Tipo",
                                        "descripcion",
                                        "Descripción",
                                        "resultado",
                                        "Resultado"),
                                button("Registrar contacto", () -> contactForm(id)),
                                secondary("Datos de contacto adicionales", () -> additionalContacts(id))));
        List<Node> clientActions = new ArrayList<>(List.of(
                button("Ver intereses", () -> interests(id)),
                secondary("Perfil de búsqueda", () -> preferences(id))));
        content.getChildren().add(flow(clientActions.toArray(Node[]::new)));
    }

    private void assignmentHistory(int client) {
        page("Historial de asignaciones", "Cambios de agente responsable del cliente #" + client + ".");
        content.getChildren().add(card(table(service.historialAsignaciones(client), "fecha", "Fecha",
                "agente_anterior_nombre", "Agente anterior", "agente_nuevo_nombre", "Agente nuevo",
                "usuario_cambio", "Modificado por", "motivo", "Motivo"),
                button("Volver a clientes", this::clients)));
    }

    private void contactForm(int client) {
        Formulario f = new Formulario();
        f.choice("tipo", "Tipo de contacto", "Llamada", "Llamada", "Mensaje", "Visita", "Reunión");
        f.rows("property", "Propiedad (opcional)", propertyOptions(), 0, true);
        f.area("descripcion", "Descripción *", "");
        f.area("resultado", "Resultado / próximos pasos", "");
        dialog(
                "Registrar contacto",
                f,
                "Guardar contacto",
                () -> {
                    int p = number(f.value("property"));
                    service.contact(
                            client,
                            p == 0 ? null : p,
                            f.value("tipo"),
                            f.value("descripcion"),
                            f.value("resultado"));
                    clients();
                });
    }

    private void consultations() {
        page("Consultas", "Revisá las consultas recibidas por propiedades y guardá la respuesta que el equipo dará al interesado.");
        TableView<Registro> inquiries = table(service.consultations(), "fecha", "Fecha", "persona", "Persona",
                "correo", "Correo", "telefono", "Teléfono", "propiedad", "Propiedad", "descripcion", "Consulta",
                "resultado", "Respuesta", "estado", "Estado", "agente", "Agente");
        content.getChildren().add(card(inquiries,
                button("Responder / actualizar", () -> respondInquiry(selected(inquiries)))));
        content.getChildren().add(label("Guardar una respuesta la registra en el sistema; no la envía por correo ni WhatsApp.", "subtitle"));
    }

    private void respondInquiry(Registro inquiry) {
        Formulario f = new Formulario(1);
        f.area("response", "Respuesta para guardar *", inquiry.text("resultado"));
        f.choice("state", "Estado", inquiry.text("estado").isBlank() ? "Respondida" : inquiry.text("estado"),
                "Respondida", "Cerrada");
        dialog("Consulta #" + inquiry.id("id_contacto"), f, "Guardar", () -> {
            service.respondInquiry(inquiry.id("id_contacto"), f.value("response"), f.value("state"));
            consultations();
        });
    }

    private void auditPage() {
        if (service.role() != 1) throw new IllegalArgumentException("La auditoría completa es exclusiva de administración.");
        page("Auditoría", "Acciones y cambios registrados por los usuarios del sistema.");
        TabPane tabs = new TabPane();
        tabs.getTabs().add(new Tab("Acciones", table(service.activity(), "fecha", "Fecha", "usuario", "Usuario",
                "accion", "Acción", "entidad", "Entidad", "id_registro", "Registro", "detalle", "Detalle")));
        tabs.getTabs().add(new Tab("Cambios", table(service.activityChanges(), "fecha", "Fecha", "usuario", "Usuario",
                "entidad", "Entidad", "id_registro", "ID", "campo", "Campo", "valor_anterior", "Antes",
                "valor_nuevo", "Después", "motivo", "Motivo")));
        tabs.getTabs().add(new Tab("Estados", table(service.allStateHistory(), "fecha", "Fecha", "titulo", "Propiedad",
                "usuario", "Usuario", "anterior", "Estado anterior", "nuevo", "Estado nuevo", "motivo", "Motivo")));
        tabs.getTabs().add(new Tab("Precios", table(service.allPriceHistory(), "fecha", "Fecha", "titulo", "Propiedad",
                "operacion", "Operación", "precio_anterior", "Antes", "precio_nuevo", "Después", "moneda", "Moneda",
                "usuario", "Usuario", "motivo", "Motivo")));
        tabs.getTabs().add(new Tab("Asignaciones", table(service.allAssignmentHistory(), "fecha", "Fecha", "tipo_entidad", "Tipo",
                "id_entidad", "Registro", "agente_anterior_nombre", "Agente anterior", "agente_nuevo_nombre", "Agente nuevo",
                "usuario_cambio", "Modificado por", "motivo", "Motivo")));
        tabs.getTabs().forEach(tab -> tab.setClosable(false));
        content.getChildren().add(tabs);
    }

    private void additionalContacts(int client) {
        page("Otros contactos del cliente #" + client, "Guardá teléfonos y correos alternativos de su ficha.");
        TableView<Registro> t = table(service.contactosAdicionales(client), "tipo", "Tipo", "valor", "Dato", "es_principal", "Principal");
        content.getChildren().addAll(card(t, button("+ Agregar contacto", () -> {
            Formulario f = new Formulario(1);
            f.choice("tipo", "Tipo", "Teléfono", "Teléfono", "Correo", "WhatsApp", "Alternativo");
            f.text("valor", "Dato de contacto", "");
            dialog("Agregar contacto", f, "Guardar", () -> {
                service.guardarContactoAdicional(client, f.value("tipo"), f.value("valor"));
                additionalContacts(client);
            });
        })), secondary("Volver a ficha", () -> clientDetails(service.clients().stream().filter(r -> r.id("id") == client).findFirst().orElseThrow())));
    }

    private List<Registro> propertyOptions() {
        List<Registro> out = new ArrayList<>();
        List<Registro> props =
                service.role() == 3
                        ? service.properties(true, "", null, null, null, "USD", 0, 0, Set.of())
                        : service.allProperties();
        for (Registro p : props) {
            Registro r = new Registro();
            r.put("id", p.id("id_propiedad"));
            r.put("etiqueta", "#" + p.id("id_propiedad") + " · " + p.text("titulo"));
            out.add(r);
        }
        return out;
    }

    private void interests(int client) {
        page(
                "Intereses y seguimiento",
                "Registrá las propiedades de interés y los avances de cada gestión.");
        TableView<Registro> t =
                table(
                        service.interests(client),
                        "id_propiedad",
                        "Propiedad ID",
                        "titulo",
                        "Propiedad",
                        "estado",
                        "Estado",
                        "acciones",
                        "Seguimiento",
                        "fecha",
                        "Fecha");
        content.getChildren()
                .add(
                        card(
                                t,
                                flow(
                                        button(
                                                "+ Vincular propiedad",
                                                () -> interestForm(client, null)),
                                        secondary(
                                                "Actualizar seguimiento",
                                                () -> interestForm(client, selected(t))),
                                        secondary(
                                                "Ver propiedad",
                                                () ->
                                                        propertyDetails(
                                                                selected(t).id("id_propiedad"))))));
    }

    private void interestForm(int client, Registro existing) {
        Formulario f = new Formulario();
        if (existing == null) f.rows("property", "Propiedad", propertyOptions(), 0, false);
        f.choice(
                "estado",
                "Estado",
                existing == null ? "Interesado" : existing.text("estado"),
                "Interesado",
                "Contactado",
                "Visita coordinada",
                "Negociación",
                "Descartado");
        f.area(
                "acciones",
                "Acciones y próximos pasos",
                existing == null ? "" : existing.text("acciones"));
        dialog(
                "Seguimiento del interés",
                f,
                "Guardar",
                () -> {
                    service.interest(
                            client,
                            existing == null
                                    ? number(f.value("property"))
                                    : existing.id("id_propiedad"),
                            f.value("estado"),
                            f.value("acciones"));
                    interests(client);
                });
    }

    private List<Registro> propertyChoices(boolean publicOnly) {
        List<Registro> source = publicOnly
                ? service.properties(true, "", null, null, null, "USD", 0, 0, Set.of())
                : service.allProperties();
        List<Registro> choices = new ArrayList<>();
        for (Registro p : source) {
            Registro row = new Registro();
            row.put("id", p.id("id_propiedad"));
            row.put("etiqueta", "#" + p.id("id_propiedad") + " · " + p.text("titulo"));
            choices.add(row);
        }
        return choices;
    }

    private LocalDateTime dateTime(Formulario form) {
        try {
            return LocalDate.parse(form.value("fecha")).atTime(LocalTime.parse(form.value("hora")));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Ingresá una fecha y hora válidas.");
        }
    }

    private void visits() {
        page("Agenda de visitas", service.role() == 3
                ? "Consultá las visitas coordinadas con tu agente."
                : "Coordiná visitas y registrá su resultado.");
        TableView<Registro> t = table(service.visitas(), "id_visita", "N.º", "propiedad", "Propiedad",
                service.role() == 3 ? "agente" : "cliente", service.role() == 3 ? "Agente" : "Cliente",
                "fecha_hora", "Fecha y hora", "estado", "Estado", "resultado", "Resultado");
        List<Node> actions = new ArrayList<>();
        if (service.role() == 3) actions.add(button("+ Solicitar visita", this::requestVisit));
        if (service.role() != 3) actions.add(secondary("Registrar resultado", () -> {
            Registro visit = selected(t);
            Formulario f = new Formulario();
            f.choice("estado", "Estado", visit.text("estado"), "Realizada", "Cancelada", "Reprogramar");
            f.area("resultado", "Resultado / próximo paso", visit.text("resultado"));
            dialog("Actualizar visita", f, "Guardar", () -> {
                service.actualizarVisita(visit.id("id_visita"), f.value("estado"), f.value("resultado"));
                visits();
            });
        }));
        content.getChildren().add(card(t, flow(actions.toArray(Node[]::new))));
    }

    private void requestVisit() {
        Formulario f = new Formulario();
        f.rows("property", "Propiedad publicada", propertyChoices(true), 0, false);
        f.date("fecha", "Fecha", LocalDate.now().plusDays(1).toString());
        f.text("hora", "Hora (HH:mm)", "10:00");
        f.area("notes", "Comentario para el agente", "");
        dialog("Solicitar visita", f, "Enviar solicitud", () -> {
            service.solicitarVisita(number(f.value("property")), dateTime(f), f.value("notes"));
            visits();
        });
    }

    private void scheduleVisit(int client) {
        Formulario f = new Formulario();
        f.rows("property", "Propiedad", propertyChoices(false), 0, false);
        if (service.role() == 1) f.rows("agent", "Agente responsable", service.agents(), 0, false);
        f.date("fecha", "Fecha", LocalDate.now().plusDays(1).toString());
        f.text("hora", "Hora (HH:mm)", "10:00");
        f.area("notes", "Observaciones", "");
        dialog("Agendar visita", f, "Guardar", () -> {
            int agent = service.role() == 1 ? number(f.value("agent")) : service.current().id("id_usuario");
            service.agendarVisita(client, number(f.value("property")), agent, dateTime(f), f.value("notes"));
            visits();
        });
    }

    private void followups() {
        page("Tareas de seguimiento", "Recordatorios para acompañar a cada cliente hasta concretar su operación.");
        TableView<Registro> t = table(service.tareasSeguimiento(), "id_tarea", "N.º", "cliente", "Cliente",
                "titulo", "Tarea", "fecha_limite", "Vencimiento", "estado", "Estado", "agente", "Agente");
        content.getChildren().add(card(t, flow(button("+ Nuevo recordatorio", this::newFollowup), secondary("Marcar completada", () -> {
            service.completarTarea(selected(t).id("id_tarea"));
            followups();
        }))));
    }

    private void newFollowup() {
        Formulario f = new Formulario();
        f.rows("client", "Cliente", service.clients(), 0, false);
        if (service.role() == 1) f.rows("agent", "Agente responsable", service.agents(), 0, false);
        f.rows("property", "Propiedad relacionada (opcional)", propertyChoices(false), 0, true);
        f.text("title", "Tarea *", "Contactar al cliente");
        f.date("fecha", "Fecha límite", LocalDate.now().toString());
        f.text("hora", "Hora límite (HH:mm)", "17:00");
        f.area("detail", "Detalle", "");
        dialog("Nuevo recordatorio", f, "Guardar", () -> {
            int prop = number(f.value("property"));
            int agent = service.role() == 1 ? number(f.value("agent")) : service.current().id("id_usuario");
            service.crearTareaSeguimiento(number(f.value("client")), prop == 0 ? null : prop, agent,
                    f.value("title"), f.value("detail"), dateTime(f));
            followups();
        });
    }

    private void makeOffer(int propertyId) {
        List<Registro> operations = new ArrayList<>();
        for (Registro offer : service.offers(propertyId)) {
            Registro item = new Registro();
            item.put("id", offer.id("id_tipo_transaccion"));
            item.put("etiqueta", offer.text("operacion") + " · " + offer.text("moneda") + " " + offer.text("precio"));
            operations.add(item);
        }
        Formulario f = new Formulario();
        f.rows("operation", "Operación", operations, operations.isEmpty() ? 0 : operations.getFirst().id("id"), false);
        f.text("amount", "Monto ofrecido *", "");
        f.choice("currency", "Moneda", operations.isEmpty() ? "USD" : service.offers(propertyId).getFirst().text("moneda"), "USD", "ARS");
        f.date("expiration", "Vigencia hasta (opcional)", "");
        f.area("notes", "Comentario", "");
        dialog("Enviar oferta", f, "Enviar", () -> {
            LocalDate expiry = f.value("expiration").isBlank() ? null : LocalDate.parse(f.value("expiration"));
            int offerId = service.crearOferta(propertyId, number(f.value("operation")), money(f.value("amount")),
                    f.value("currency"), expiry, f.value("notes"));
            info("La oferta #" + offerId + " fue enviada a tu agente.");
            offersAndReservations();
        });
    }

    private void offersAndReservations() {
        page("Ofertas y reservas", "Seguí las propuestas económicas y las reservas de cada operación.");
        TableView<Registro> offerTable = service.role() == 1
                ? table(service.ofertasComerciales(), "id_oferta", "N.º", "propiedad", "Propiedad", "cliente", "Cliente", "agente", "Agente", "operacion", "Operación", "monto", "Monto", "moneda", "Moneda", "estado", "Estado")
                : table(service.ofertasComerciales(), "id_oferta", "N.º", "propiedad", "Propiedad", service.role() == 3 ? "agente" : "cliente", service.role() == 3 ? "Agente" : "Cliente", "operacion", "Operación", "monto", "Monto", "moneda", "Moneda", "estado", "Estado");
        HBox offerActions = new HBox(10);
        if (service.role() != 3) {
            offerActions.getChildren().addAll(
                    secondary("Aceptar oferta", () -> reviewOffer(selected(offerTable), true)),
                    secondary("Rechazar oferta", () -> reviewOffer(selected(offerTable), false)));
            if (service.role() == 2) offerActions.getChildren().add(button("Solicitar reserva", () -> reserve(selected(offerTable))));
        }
        TableView<Registro> reservationTable = table(service.reservasComerciales(), "id_reserva", "N.º", "propiedad", "Propiedad",
                service.role() == 3 ? "agente" : "cliente", service.role() == 3 ? "Agente" : "Cliente",
                "monto", "Importe", "moneda", "Moneda", "fecha_limite", "Vencimiento", "estado", "Estado", "estado_pago", "Pago");
        HBox reservationActions = new HBox(10);
        if (service.role() == 1) reservationActions.getChildren().addAll(
                button("Autorizar reserva", () -> updateReservation(selected(reservationTable), "autorizar")),
                secondary("Cancelar reserva", () -> updateReservation(selected(reservationTable), "cancelar")),
                secondary("Actualizar pago", () -> updateReservation(selected(reservationTable), "pago")));
        content.getChildren().addAll(card(label("Ofertas recibidas", "section-title"), offerTable, offerActions),
                card(label("Reservas", "section-title"), reservationTable, reservationActions));
    }

    private void reviewOffer(Registro offer, boolean accept) {
        Formulario f = new Formulario(1);
        f.area("comment", "Respuesta para el cliente", "");
        dialog(accept ? "Aceptar oferta" : "Rechazar oferta", f, "Confirmar", () -> {
            service.revisarOferta(offer.id("id_oferta"), accept, f.value("comment"));
            offersAndReservations();
        });
    }

    private void reserve(Registro offer) {
        Formulario f = new Formulario();
        f.date("fecha", "Vencimiento", LocalDate.now().plusDays(3).toString());
        f.text("hora", "Hora límite (HH:mm)", "17:00");
        f.area("notes", "Observaciones", "");
        dialog("Solicitar reserva", f, "Enviar a administración", () -> {
            service.solicitarReserva(offer.id("id_oferta"), dateTime(f), f.value("notes"));
            offersAndReservations();
        });
    }

    private void updateReservation(Registro reservation, String action) {
        if (action.equals("pago")) {
            Formulario f = new Formulario();
            f.choice("payment", "Estado de pago", reservation.text("estado_pago"), "Pendiente", "Pagado", "Rechazado");
            dialog("Actualizar pago de reserva #" + reservation.id("id_reserva"), f, "Guardar", () -> {
                service.actualizarReserva(reservation.id("id_reserva"), null, f.value("payment"));
                offersAndReservations();
            });
            return;
        }
        service.actualizarReserva(reservation.id("id_reserva"), action, null);
        offersAndReservations();
    }

    private void preferences(int client) {
        Registro p = service.preference(client);
        page(
                "Perfil de búsqueda",
                "Guardá las preferencias del cliente y encontrá propiedades compatibles.");
        Formulario f = new Formulario();
        f.rows(
                "id_tipo_transaccion",
                "Operación",
                service.lookup("tipos_transaccion"),
                p.id("id_tipo_transaccion"),
                false);
        f.rows(
                "id_tipo_propiedad",
                "Tipo de propiedad",
                service.lookup("tipos_propiedad"),
                p.id("id_tipo_propiedad"),
                true);
        f.text("presupuesto_maximo", "Presupuesto máximo *", p.text("presupuesto_maximo"));
        f.choice("moneda", "Moneda", p.text("moneda"), "USD", "ARS");
        f.text("zona_interes", "Localidad o zona de interés", p.text("zona_interes"));
        f.text(
                "ambientes_minimos",
                "Ambientes mínimos",
                p.isEmpty() ? "0" : p.text("ambientes_minimos"));
        f.text(
                "dormitorios_minimos",
                "Dormitorios mínimos",
                p.isEmpty() ? "0" : p.text("dormitorios_minimos"));
        f.text(
                "superficie_minima_m2",
                "Superficie mínima m²",
                p.isEmpty() ? "0" : p.text("superficie_minima_m2"));
        f.date(
                "fecha_estimada_concretar",
                "Fecha estimada de operación",
                p.text("fecha_estimada_concretar"));
        f.check("requiere_venta_previa", "Necesita vender antes", p.flag("requiere_venta_previa"));
        Set<Integer> chosen = new HashSet<>();
        service.preferenceFeatures(client).forEach(r -> chosen.add(r.id("id")));
        Map<Integer, CheckBox> features = featureChecks(chosen);
        content.getChildren()
                .add(
                        card(
                                f,
                                label("Características deseadas", "section-title"),
                                flow(features.values().toArray(Node[]::new)),
                                button(
                                        "Guardar y buscar coincidencias",
                                        () -> {
                                            service.savePreference(
                                                    client, f.values(), checked(features));
                                            preferences(client);
                                        })));
        if (!p.isEmpty()) {
            FlowPane matches = new FlowPane(18, 18);
            List<Registro> results = service.matches(client);
            results.forEach(r -> matches.getChildren().add(propertyCard(r)));
            content.getChildren()
                    .addAll(
                            label(results.size() + " propiedades compatibles", "section-title"),
                            matches);
        }
    }

    private void agreements() {
        page(
                "Propietarios y convenios",
                "Condiciones acordadas con propietarios y otros profesionales inmobiliarios.");
        content.getChildren()
                .addAll(
                        button(
                                "+ Registrar convenio",
                                () -> {
                                    Formulario f = new Formulario();
                                    f.rows("owner", "Propietario", service.clients(), 0, false);
                                    f.rows(
                                            "property",
                                            "Propiedad (opcional)",
                                            propertyOptions(),
                                            0,
                                            true);
                                    f.text(
                                            "professional",
                                            "Profesional / inmobiliaria colaboradora",
                                            "");
                                    f.date("date", "Fecha", LocalDate.now().toString());
                                    f.area("conditions", "Condiciones *", "");
                                    dialog(
                                            "Nuevo convenio",
                                            f,
                                            "Guardar",
                                            () -> {
                                                int p = number(f.value("property"));
                                                service.agreement(
                                                        number(f.value("owner")),
                                                        p == 0 ? null : p,
                                                        f.value("professional"),
                                                        f.value("conditions"),
                                                        LocalDate.parse(f.value("date")));
                                                agreements();
                                            });
                                }),
                        card(
                                table(
                                        service.agreements(),
                                        "id_convenio",
                                        "ID",
                                        "propietario",
                                        "Propietario",
                                        "id_propiedad",
                                        "Propiedad ID",
                                        "profesional",
                                        "Profesional",
                                        "condiciones",
                                        "Condiciones",
                                        "fecha",
                                        "Fecha")));
    }

    private void buildings() {
        page(
                "Edificios y unidades",
                "Cada unidad es una propiedad independiente vinculada a su edificio.");
        TableView<Registro> t =
                table(
                        service.buildings(),
                        "id_edificio",
                        "ID",
                        "nombre",
                        "Edificio",
                        "direccion",
                        "Dirección",
                        "localidad",
                        "Localidad",
                        "unidades",
                        "Unidades");
        content.getChildren()
                .addAll(
                        button(
                                "+ Nuevo edificio",
                                () -> {
                                    Formulario f = new Formulario();
                                    f.text("name", "Nombre *", "");
                                    f.text("address", "Dirección *", "");
                                    f.text("city", "Localidad *", "");
                                    dialog(
                                            "Crear edificio",
                                            f,
                                            "Guardar",
                                            () -> {
                                                service.building(
                                                        f.value("name"),
                                                        f.value("address"),
                                                        f.value("city"));
                                                buildings();
                                            });
                                }),
                        card(
                                t,
                                secondary(
                                        "Ver unidades",
                                        () -> {
                                            Registro e = selected(t);
                                            page(
                                                    "Unidades · " + e.text("nombre"),
                                                    e.text("direccion"));
                                            TableView<Registro> units =
                                                    table(
                                                            service.allProperties().stream()
                                                                    .filter(
                                                                            p ->
                                                                                    p.id(
                                                                                                    "id_edificio")
                                                                                            == e.id(
                                                                                                    "id_edificio"))
                                                                    .toList(),
                                                            "id_propiedad",
                                                            "ID",
                                                            "numero_unidad",
                                                            "Unidad",
                                                            "titulo",
                                                            "Propiedad",
                                                            "comercial",
                                                            "Estado");
                                            content.getChildren()
                                                    .add(
                                                            card(
                                                                    units,
                                                                    button(
                                                                            "Ver ficha",
                                                                            () ->
                                                                                    propertyDetails(
                                                                                            selected(
                                                                                                            units)
                                                                                                    .id(
                                                                                                            "id_propiedad")))));
                                        })));
    }

    private void operations() {
        page(
                "Ventas y alquileres",
                "Historial de cierres. Una propiedad puede volver al mercado sin perder operaciones"
                        + " anteriores.");
        List<Registro> ops = service.operations();
        content.getChildren()
                .add(
                        card(
                                table(
                                        ops,
                                        "id_operacion",
                                        "ID",
                                        "titulo",
                                        "Propiedad",
                                        "cliente",
                                        "Cliente",
                                        "operacion",
                                        "Operación",
                                        "precio_final",
                                        "Importe",
                                        "moneda",
                                        "Moneda",
                                        "comision_total",
                                        "Comisión",
                                        "fecha_cierre",
                                        "Fecha"),
                                button("Exportar CSV", () -> export(ops, "operaciones.csv"))));
    }

    private void notifications() {
        page("Notificaciones", "Novedades sobre tus solicitudes y operaciones.");
        TableView<Registro> t =
                table(
                        service.notifications(),
                        "fecha",
                        "Fecha",
                        "mensaje",
                        "Mensaje",
                        "leida",
                        "Leída (1 = sí)");
        content.getChildren()
                .add(
                        card(
                                t,
                                button(
                                        "Marcar como leída",
                                        () -> {
                                            service.readNotification(
                                                    selected(t).id("id_notificacion"));
                                            notifications();
                                        })));
    }

    private void users() {
        page("Usuarios", "Creá cuentas para el equipo y administrá su acceso al sistema.");
        TableView<Registro> t =
                table(
                        service.users(),
                        "id",
                        "ID",
                        "etiqueta",
                        "Nombre",
                        "email",
                        "Correo",
                        "rol",
                        "Rol",
                        "activo",
                        "Activo (1 = sí)");
        content.getChildren()
                .addAll(
                        button("+ Crear usuario", () -> newUser(true)),
                        card(
                                t,
                                flow(
                                        secondary(
                                                "Activar",
                                                () -> {
                                                    service.setActive(selected(t).id("id"), true);
                                                    users();
                                                }),
                                        secondary(
                                                "Restablecer contraseña",
                                                () -> resetUserPassword(selected(t))),
                                        secondary(
                                                "Desactivar",
                                                () -> {
                                                    Registro u = selected(t);
                                                    Alert a =
                                                            new Alert(
                                                                    Alert.AlertType.CONFIRMATION,
                                                                    "¿Desactivar el acceso de "
                                                                            + u.text("etiqueta")
                                                                            + "?",
                                                                    ButtonType.YES,
                                                                    ButtonType.NO);
                                                    a.initOwner(stage);
                                                    a.setTitle("Confirmar desactivación");
                                                    a.setHeaderText(null);
                                                    styleDialog(a);
                                                    ((Button) a.getDialogPane().lookupButton(ButtonType.YES)).setText("Desactivar");
                                                    ((Button) a.getDialogPane().lookupButton(ButtonType.NO)).setText("Cancelar");
                                                    if (a.showAndWait().orElse(ButtonType.NO)
                                                            == ButtonType.YES) {
                                                        service.setActive(u.id("id"), false);
                                                        users();
                                                    }
                                                }))));
    }

    private void resetUserPassword(Registro user) {
        Formulario form = new Formulario(1);
        PasswordField password = form.password("password", "Contraseña temporal (10 caracteres o más)");
        PasswordField confirmation = form.password("confirmation", "Repetir contraseña temporal");
        dialog(
                "Restablecer acceso · " + user.text("etiqueta"),
                card(
                        form,
                        label(
                                "Compartí esta contraseña con el usuario por un canal privado. "
                                        + "Después podrá cambiarla desde Mi perfil.",
                                "subtitle")),
                "Restablecer contraseña",
                () -> {
                    if (!password.getText().equals(confirmation.getText()))
                        throw new IllegalArgumentException("Las contraseñas no coinciden.");
                    service.resetUserPassword(user.id("id"), password.getText());
                    users();
                    info("La contraseña temporal quedó guardada.");
                });
    }

    private void agents() {
        page("Agentes", "Consultá el equipo, sus clientes vinculados por actividad y el estado de sus cuentas.");
        TableView<Registro> t = table(new PanelAdministradorDAO(service.db).listAgents(),
                "id_usuario", "ID", "agente", "Agente", "email", "Correo", "telefono", "Teléfono",
                "clientes", "Clientes asignados", "propiedades", "Propiedades creadas", "activo", "Activo");
        content.getChildren().addAll(
                row(button("+ Crear agente", () -> newUser(true)), secondary("Ver todos los usuarios", this::users)),
                card(t, flow(
                        button("Ver clientes vinculados", () -> {
                            Registro agent = selected(t);
                            page("Clientes vinculados a " + agent.text("agente"), "La relación se forma por consultas, solicitudes, visitas, tareas, ofertas e inmuebles.");
                            TableView<Registro> clients = table(service.clientsForAgent(agent.id("id_usuario")), "id", "ID", "etiqueta", "Cliente", "email", "Correo", "telefono", "Teléfono");
                            content.getChildren().add(card(clients, button("Ver ficha", () -> clientDetails(selected(clients)))));
                        }),
                        secondary("Activar / desactivar", () -> {
                            Registro agent = selected(t);
                            boolean active = "1".equals(agent.text("activo")) || "true".equalsIgnoreCase(agent.text("activo"));
                            service.setActive(agent.id("id_usuario"), !active);
                            agents();
                        }))));
    }

    private void profile() {
        Registro u = service.current();
        page("Mi perfil", u.text("email") + " · Documento " + u.text("dni"));
        Formulario f = new Formulario();
        for (String k : List.of("nombre", "apellido", "telefono", "direccion", "localidad"))
            f.text(k, k.substring(0, 1).toUpperCase() + k.substring(1), u.text(k));
        content.getChildren()
                .add(
                        card(
                                f,
                                button(
                                        "Guardar perfil",
                                        () -> {
                                            service.updateProfile(f.values());
                                            shell();
                                            profile();
                                        })));
        Formulario pw = new Formulario();
        PasswordField old = pw.password("old", "Contraseña actual");
        PasswordField next = pw.password("next", "Contraseña nueva (mínimo 10 caracteres)");
        content.getChildren()
                .add(
                        card(
                                label("Cambiar contraseña", "section-title"),
                                pw,
                                button(
                                        "Actualizar contraseña",
                                        () -> {
                                            service.changePassword(old.getText(), next.getText());
                                            old.clear();
                                            next.clear();
                                            info("Contraseña actualizada.");
                                        })));
    }

    private void settings() {
        page("Configuración", "Información de esta instalación de escritorio.");
        content.getChildren().add(card(
                label(service.db.demo ? "Modo demostración · H2 local" : "Conexión MySQL / MariaDB", "section-title"),
                label("Archivos: " + service.db.storage + "\nLa conexión se configura en config.properties, junto al pom.xml. Reiniciá la aplicación después de modificarla.", "subtitle")));
        if (service.role() == 1) {
            Map<String, Registro> settings = new HashMap<>();
            service.configuracionAlertas().forEach(r -> settings.put(r.text("clave"), r));
            Formulario f = new Formulario();
            f.text("dias_cliente_sin_contacto", "Días sin contacto del cliente",
                    settings.get("dias_cliente_sin_contacto").text("valor"));
            f.text("dias_propiedad_sin_actividad", "Días sin actividad de una propiedad",
                    settings.get("dias_propiedad_sin_actividad").text("valor"));
            f.text("dias_aviso_reserva", "Aviso antes del vencimiento de reserva (días)",
                    settings.get("dias_aviso_reserva").text("valor"));
            content.getChildren().add(card(label("Umbrales de alertas", "section-title"), f,
                    button("Guardar configuración", () -> {
                        service.guardarConfiguracionAlertas(f.values());
                        info("Los umbrales de alertas se guardaron.");
                    })));
        }
        if (service.role() != 1)
            content.getChildren().add(secondary("Editar mi perfil y contraseña", this::profile));
    }

    private void reports() {
        page("Reportes", "Métricas calculadas a partir de los registros del sistema.");
        List<Registro> props = service.allProperties(), ops = service.operations();
        content.getChildren()
                .add(
                        flow(
                                stat("Clientes", service.clients().size()),
                                stat("Propiedades", props.size()),
                                stat(
                                        "Ventas cerradas",
                                        ops.stream()
                                                .filter(r -> r.id("id_tipo_transaccion") == 1)
                                                .count()),
                                stat(
                                        "Alquileres registrados",
                                        ops.stream()
                                                .filter(r -> r.id("id_tipo_transaccion") != 1)
                                                .count())));
        for (String currency : List.of("USD", "ARS")) {
            BigDecimal total =
                    ops.stream()
                            .filter(r -> r.text("moneda").equals(currency))
                            .map(r -> r.money("precio_final"))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal commissions =
                    ops.stream()
                            .filter(r -> r.text("moneda").equals(currency))
                            .map(r -> r.money("comision_total"))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            content.getChildren()
                    .add(
                            card(
                                    label("Totales en " + currency, "section-title"),
                                    label(
                                            "Operaciones: "
                                                    + total
                                                    + " · Comisiones: "
                                                    + commissions,
                                            "price")));
        }
        List<Registro> activity = service.activity();
        content.getChildren()
                .add(
                        card(
                                label("Auditoría · últimos 200 eventos", "section-title"),
                                table(
                                        activity,
                                        "fecha",
                                        "Fecha",
                                        "usuario",
                                        "Usuario",
                                        "accion",
                                        "Acción",
                                        "entidad",
                                        "Entidad",
                                        "id_registro",
                                        "Registro",
                                        "detalle",
                                        "Detalle"),
                                button(
                                        "Exportar auditoría CSV",
                                        () -> export(activity, "auditoria.csv"))));
    }

    private void export(List<Registro> rows, String filename) {
        if (rows.isEmpty()) throw new IllegalArgumentException("No hay datos para exportar.");
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(filename);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        java.io.File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        List<String> keys = new ArrayList<>(rows.getFirst().keySet());
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(String.join(";", keys)).append("\r\n");
        for (Registro r : rows) {
            List<String> cells = new ArrayList<>();
            for (String key : keys) {
                String v = r.text(key);
                if (v.matches("^[=+@\\-].*")) v = "'" + v;
                cells.add("\"" + v.replace("\"", "\"\"") + "\"");
            }
            csv.append(String.join(";", cells)).append("\r\n");
        }
        try {
            Files.writeString(file.toPath(), csv);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo guardar el CSV", e);
        }
        info("Reporte exportado en " + file);
    }
}
