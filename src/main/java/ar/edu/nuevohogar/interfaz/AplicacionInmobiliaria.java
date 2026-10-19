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
import ar.edu.nuevohogar.interfaz.autenticacion.FormularioAcceso;

import javafx.application.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

/** Presentación JavaFX: catálogo y paneles por rol sobre los mismos servicios. */
public final class AplicacionInmobiliaria extends Application {
    private ServicioInmobiliaria service;
    private Stage stage;
    private final BorderPane root = new BorderPane();
    private VBox content;
    private String activePage = "Catálogo";

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
                    else catalog();
                });
        init.setOnFailed(
                e -> {
                    VBox box =
                            new VBox(
                                    18,
                                    title("No pudimos abrir la base de datos"),
                                    label(
                                            "Revisá config.properties, que MySQL esté iniciado y"
                                                    + " que la base tenga el esquema del proyecto.",
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
                        label("Tu próximo hogar, está aquí", "footer"));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        header.getChildren().addAll(brand, gap);
        if (!service.signedIn()) {
            header.getChildren()
                    .addAll(
                            navButton("Inicio", this::catalog),
                            navButton("Comprar", () -> catalog(1)),
                            navButton("Alquilar", () -> catalog(2)),
                            navButton("Vender", this::register),
                            secondary("Iniciar sesión", this::login),
                            button("Registrarme", this::register));
            root.setTop(header);
            root.setLeft(null);
            return;
        }
        if (service.role() == 3) {
            header.getChildren()
                    .addAll(
                            navButton("Inicio", this::catalog),
                            navButton("Comprar", () -> catalog(1)),
                            navButton("Alquilar", () -> catalog(2)),
                            navButton("Vender", () -> propertyForm(null)),
                            navButton("Mis solicitudes", this::requests));
            MenuButton account = new MenuButton("Mi cuenta  ▾");
            account.getStyleClass().add("account-menu");
            MenuItem profileItem = new MenuItem("Mi perfil");
            MenuItem propertiesItem = new MenuItem("Mis propiedades");
            MenuItem requestsItem = new MenuItem("Mis solicitudes");
            MenuItem interestsItem = new MenuItem("Mis intereses");
            MenuItem preferencesItem = new MenuItem("Mi búsqueda");
            MenuItem visitsItem = new MenuItem("Mis visitas");
            MenuItem logoutItem = new MenuItem("Cerrar sesión");
            profileItem.setOnAction(e -> safe(this::profile));
            propertiesItem.setOnAction(e -> safe(this::propertyList));
            requestsItem.setOnAction(e -> safe(this::requests));
            interestsItem.setOnAction(
                    e -> safe(() -> interests(service.current().id("id_usuario"))));
            preferencesItem.setOnAction(
                    e -> safe(() -> preferences(service.current().id("id_usuario"))));
            visitsItem.setOnAction(e -> safe(this::visits));
            logoutItem.setOnAction(
                    e -> {
                        service.logout();
                        shell();
                        catalog();
                    });
            account.getItems()
                    .addAll(
                            profileItem,
                            propertiesItem,
                            requestsItem,
                            interestsItem,
                            preferencesItem,
                            visitsItem,
                            new SeparatorMenuItem(),
                            logoutItem);
            header.getChildren().add(account);
            root.setTop(header);
            root.setLeft(null);
            return;
        }
        if (service.role() == 1) {
            header.getChildren().addAll(
                    navButton("Catálogo", this::catalog),
                    label("◉", "admin-avatar"),
                    new VBox(2, label("Admin", "admin-name"), label("Administrador", "admin-caption")),
                    secondary("Notificaciones", this::notifications),
                    button("Cerrar sesión", () -> {
                        service.logout();
                        shell();
                        catalog();
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
                    this::buildings, this::settings);
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
            header.getChildren()
                    .addAll(
                            navButton("Catálogo", this::catalog),
                            button("Mi perfil", this::profile),
                            label(
                                    service.current().text("nombre") + " · " + roleName(),
                                    "subtitle"),
                            secondary(
                                    "Cerrar sesión",
                                    () -> {
                                        service.logout();
                                        shell();
                                        catalog();
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
                        this::buildings, this::operations, this::reports, this::notifications)
                : PanelCliente.menu(this::dashboard, this::catalog, this::requests, this::visits,
                        this::offersAndReservations,
                        () -> interests(service.current().id("id_usuario")),
                        () -> preferences(service.current().id("id_usuario")), this::notifications,
                        () -> propertyForm(null));
        for (ElementoMenuPanel item : roleMenu) nav.getChildren().add(button(item.nombre(), item.accion()));
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        nav.getChildren()
                .addAll(
                        spacer,
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

    private Button navButton(String text, Runnable action) {
        Button b = button(text, action);
        b.getStyleClass().add("nav-link");
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
            safe(action);
        });
        return button;
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
            message +=
                    "\n\nDetalle de MySQL: "
                            + Objects.toString(sql.getMessage(), "Error SQL")
                            + (sql.getSQLState() == null ? "" : "\nCódigo SQL: " + sql.getSQLState());
        } else if (cause != e && cause.getMessage() != null) {
            message += "\n\nDetalle: " + cause.getMessage();
        }
        a.setContentText(message);
        a.setResizable(true);
        a.getDialogPane().setPrefWidth(560);
        a.showAndWait();
    }

    private void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.initOwner(stage);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private void page(String title, String subtitle) {
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
        page(
                "Bienvenido/a a Nuevo Hogar",
                "Ingresá para administrar tus propiedades y continuar tus gestiones.");
        Formulario f = FormularioAcceso.inicioSesion();
        VBox left =
                card(
                        house(340, 215, 0),
                        title("Tu próximo hogar\nempieza acá."),
                        label(
                                "Encontrá propiedades, seguí tus solicitudes y mantené toda tu"
                                        + " información en un solo lugar.",
                                "subtitle"));
        HBox.setHgrow(left, Priority.ALWAYS);
        VBox right =
                card(
                        label("Iniciar sesión", "section-title"),
                        f,
                        button(
                                "Iniciar sesión",
                                () -> {
                                    service.login(f.value("email"), f.value("password"));
                                    shell();
                                    if (service.role() == 3) catalog();
                                    else dashboard();
                                }),
                        secondary("Crear una cuenta", this::register));
        HBox.setHgrow(right, Priority.ALWAYS);
        content.getChildren().add(row(left, right));
        if (service.db.demo)
            content.getChildren()
                    .add(
                            card(
                                    label("MODO DEMOSTRACIÓN · datos ficticios", "section-title"),
                                    label(
                                            "Administrador: admin@nuevohogar.demo\n"
                                                    + "Agente: agente@nuevohogar.demo\n"
                                                    + "Cliente: cliente@nuevohogar.demo\n"
                                                    + "Contraseña para los tres: "
                                                    + DatosDemostracion.PASSWORD,
                                            "subtitle")));
    }

    private void register() {
        page(
                "Crear cuenta",
                "Completá tus datos para buscar, consultar y solicitar publicaciones.");
        Formulario personal = FormularioAcceso.datosPersonales();
        Formulario contact = FormularioAcceso.datosDeContacto();
        PasswordField pass = (PasswordField) contact.control("password");
        PasswordField confirm = (PasswordField) contact.control("confirm");
        Formulario address = new Formulario();
        address.text("direccion", "Dirección", "");
        address.text("localidad", "Localidad", "");
        Formulario[] steps = {personal, contact, address};
        String[] names = {
            "1  Datos personales", "2  Datos de contacto", "3  Dirección", "4  Finalizar"
        };
        int[] index = {0};
        VBox area = card();
        VBox nav = new VBox(16);
        nav.setMinWidth(210);
        for (String name : names) nav.getChildren().add(label(name, "section-title"));
        Runnable[] show = {null};
        show[0] =
                () -> {
                    area.getChildren()
                            .setAll(
                                    label(
                                            "Crear cuenta · Paso " + (index[0] + 1) + " de 4",
                                            "section-title"),
                                    steps[index[0]]);
                    HBox controls = row();
                    if (index[0] > 0)
                        controls.getChildren()
                                .add(
                                        secondary(
                                                "Volver",
                                                () -> {
                                                    index[0]--;
                                                    show[0].run();
                                                }));
                    controls.getChildren()
                            .add(
                                    button(
                                            index[0] == 2 ? "Crear cuenta" : "Siguiente",
                                            () -> {
                                                if (index[0] == 0) {
                                                    for (String k :
                                                            List.of("nombre", "apellido", "dni"))
                                                        if (personal.value(k).isBlank())
                                                            throw new IllegalArgumentException(
                                                                    "Completá tus datos"
                                                                            + " personales.");
                                                }
                                                if (index[0] == 1) {
                                                    if (contact.value("email").isBlank()
                                                            || pass.getText().length() < 10
                                                            || !pass.getText()
                                                                    .equals(confirm.getText()))
                                                        throw new IllegalArgumentException(
                                                                "Revisá el correo y las"
                                                                    + " contraseñas; deben"
                                                                    + " coincidir y tener al menos"
                                                                    + " 10 caracteres.");
                                                }
                                                if (index[0] < 2) {
                                                    index[0]++;
                                                    show[0].run();
                                                } else {
                                                    Map<String, String> d =
                                                            new HashMap<>(personal.values());
                                                    d.putAll(contact.values());
                                                    d.putAll(address.values());
                                                    service.register(d, pass.getText());
                                                    service.login(d.get("email"), pass.getText());
                                                    shell();
                                                    catalog();
                                                    info(
                                                            "Tu cuenta se creó correctamente. Ya"
                                                                    + " podés explorar las propiedades"
                                                                    + " en venta y alquiler.");
                                                }
                                            }));
                    area.getChildren().add(controls);
                };
        show[0].run();
        HBox.setHgrow(area, Priority.ALWAYS);
        content.getChildren().add(row(nav, area));
    }

    private void catalog() {
        catalog(0);
    }

    private void catalog(int operationFilter) {
        page(
                operationFilter == 1
                        ? "Propiedades en venta"
                        : operationFilter == 2
                                ? "Propiedades en alquiler"
                                : "Encontrá la propiedad que estás buscando",
                "Casas, departamentos y terrenos. Un lugar para cada nueva etapa.");
        HBox hero =
                row(
                        new VBox(
                                12,
                                title("El próximo capítulo\nde tu vida."),
                                label(
                                        "Explorá las propiedades aprobadas por nuestro equipo.",
                                        "subtitle")),
                        house(220, 130, 0));
        hero.getStyleClass().add("hero");
        content.getChildren().add(hero);
        Formulario filter = new Formulario(4);
        filter.text("q", "Buscar por título, dirección o localidad", "");
        filter.rows("type", "Tipo de propiedad", service.lookup("tipos_propiedad"), 0, true);
        filter.rows(
                "operation", "Operación", service.lookup("tipos_transaccion"), operationFilter, true);
        filter.text("max", "Precio máximo (opcional)", "");
        filter.choice("currency", "Moneda del precio máximo", "USD", "USD", "ARS");
        filter.text("beds", "Dormitorios mínimos", "0");
        filter.text("capacity", "Capacidad mínima", "0");
        Map<Integer, CheckBox> features = featureChecks(Set.of());
        FlowPane cards = new FlowPane(18, 18);
        Label count = label("", "catalog-result-count");
        Runnable search =
                () -> {
                    BigDecimal max =
                            filter.value("max").isBlank() ? null : money(filter.value("max"));
                    if (max != null && max.signum() < 0)
                        throw new IllegalArgumentException(
                                "El precio máximo no puede ser negativo.");
                    int type = number(filter.value("type")),
                            operation = number(filter.value("operation"));
                    int beds = number(filter.value("beds")),
                            capacity = number(filter.value("capacity"));
                    if (beds < 0 || capacity < 0)
                        throw new IllegalArgumentException(
                                "Los filtros numéricos no pueden ser negativos.");
                    List<Registro> rows =
                            service.properties(
                                    true,
                                    filter.value("q"),
                                    type == 0 ? null : type,
                                    operation == 0 ? null : operation,
                                    max,
                                    filter.value("currency"),
                                    beds,
                                    capacity,
                                    checked(features));
                    cards.getChildren().clear();
                    count.setText(rows.size() + " propiedades encontradas");
                    for (Registro p : rows) cards.getChildren().add(propertyCard(p, operation));
                    if (rows.isEmpty())
                        cards.getChildren()
                                .add(
                                        card(
                                                label(
                                                        "No encontramos coincidencias",
                                                        "section-title"),
                                                label(
                                                        "Probá con otra localidad o ampliá los"
                                                                + " filtros.",
                                                        "subtitle")));
                };
        TitledPane advanced =
                new TitledPane("Características", flow(features.values().toArray(Node[]::new)));
        advanced.setExpanded(false);
        HBox resultsTitle = new HBox(12,
                label("Propiedades destacadas", "catalog-section-title"),
                count);
        resultsTitle.setAlignment(Pos.CENTER_LEFT);
        content.getChildren()
                .addAll(card(filter, advanced, button("Buscar propiedades", search)), resultsTitle, cards);
        search.run();
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

    private List<Path> propertyImagePaths(Registro property) {
        return service.attachments(property.id("id_propiedad"), false).stream()
                .filter(file -> file.text("tipo").equals("Imagen"))
                .map(file -> service.attachmentPath(property.id("id_propiedad"), file.id("id"), false))
                .filter(Files::isRegularFile)
                .toList();
    }

    private Node photoFrame(Path path, double width, double height) {
        Image image = new Image(path.toUri().toString());
        if (image.isError()) return house(width, height, path.hashCode());
        ImageView view = new ImageView(image);
        double scale = Math.max(width / image.getWidth(), height / image.getHeight());
        view.setFitWidth(image.getWidth() * scale);
        view.setFitHeight(image.getHeight() * scale);
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
        List<Path> photos = propertyImagePaths(property);
        return photos.isEmpty()
                ? house(width, height, property.id("id_propiedad"))
                : photoFrame(photos.getFirst(), width, height);
    }

    private Node propertyGallery(Registro property, double width, double height) {
        List<Path> photos = propertyImagePaths(property);
        StackPane main = new StackPane();
        main.setPrefSize(width, height);
        main.setMinSize(width, height);
        main.setMaxSize(width, height);
        main.getStyleClass().add("detail-gallery-main");
        if (photos.isEmpty()) {
            Label empty = label("FOTOGRAFÍAS PENDIENTES", "photo-empty-badge");
            main.getChildren().addAll(house(width, height, property.id("id_propiedad")), empty);
            StackPane.setAlignment(empty, Pos.TOP_LEFT);
            StackPane.setMargin(empty, new Insets(14));
        } else {
            main.getChildren().add(photoFrame(photos.getFirst(), width, height));
        }
        HBox thumbnails = new HBox(9);
        thumbnails.getStyleClass().add("property-thumbnails");
        for (Path photo : photos.stream().limit(6).toList()) {
            StackPane thumbnail = new StackPane(photoFrame(photo, 92, 64));
            thumbnail.getStyleClass().add("property-thumbnail");
            thumbnail.setOnMouseClicked(e -> main.getChildren().setAll(photoFrame(photo, width, height)));
            thumbnails.getChildren().add(thumbnail);
        }
        VBox gallery = new VBox(10, main, thumbnails);
        gallery.getStyleClass().add("property-gallery");
        return gallery;
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

        StackPane image = new StackPane(propertyVisual(property, 258, 164));
        image.setPrefSize(258, 164);
        image.setMinSize(258, 164);
        image.setMaxSize(258, 164);
        Label badge = label(operation.equalsIgnoreCase("Alquiler") ? "Alquiler" : "Venta", "property-operation-badge");
        StackPane.setAlignment(badge, Pos.TOP_LEFT);
        StackPane.setMargin(badge, new Insets(12));
        image.getChildren().add(badge);

        Label title = label(property.text("titulo"), "property-card-title");
        title.setMinHeight(42);
        Label location = label("⌖  " + property.text("localidad"), "property-card-location");
        HBox facts = new HBox(12,
                propertyMiniFact("⌂", metric(property, "dormitorios", "")),
                propertyMiniFact("♧", metric(property, "banos", "")),
                propertyMiniFact("▧", metric(property, "superficie_total_m2", " m²")));
        facts.getStyleClass().add("property-card-facts");
        Label price = label(offer == null ? "Consultar precio" : formatOffer(offer), "property-card-price");
        Button details = button("Ver detalle  →", () -> propertyDetails(id));
        details.getStyleClass().add("property-detail-button");
        details.setMaxWidth(Double.MAX_VALUE);

        VBox body = new VBox(8, label(property.text("tipo"), "property-type"), title, location, facts, price, details);
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

        Button back = secondary("←  Volver al catálogo", this::catalog);
        VBox facts = new VBox(14);
        facts.getStyleClass().add("property-detail-facts");
        facts.getChildren().addAll(
                row(label(property.text("tipo"), "property-type"), label(property.text("comercial"), "property-status-badge")),
                label("⌖  " + property.text("localidad"), "property-detail-location"));
        for (Registro offer : offers) {
            HBox priceLine = new HBox(12, label(offer.text("operacion"), "property-price-caption"), label(formatOffer(offer), "property-detail-price"));
            priceLine.setAlignment(Pos.CENTER_LEFT);
            facts.getChildren().add(priceLine);
        }
        HBox specifications = new HBox(12,
                propertyDetailFact("⌂", metric(property, "dormitorios", ""), "dormitorios"),
                propertyDetailFact("♧", metric(property, "banos", ""), "baños"),
                propertyDetailFact("▧", metric(property, "superficie_total_m2", " m²"), "totales"),
                propertyDetailFact("▧", metric(property, "superficie_cubierta_m2", " m²"), "cubiertos"));
        facts.getChildren().addAll(specifications,
                label("Ambientes: " + metric(property, "ambientes", "") + "   ·   Antigüedad: " + metric(property, "antiguedad_anos", " años"), "property-extra-facts"));

        Button contact = button("Contactar", () -> contactProperty(id));
        contact.getStyleClass().add("property-detail-button");
        Button favorite = secondary("♡  Guardar", () -> saveFavorite(id));
        HBox actions = new HBox(10, contact, favorite);
        if (service.signedIn() && service.role() == 3) {
            actions.getChildren().add(secondary("Solicitar visita", this::requestVisit));
            actions.getChildren().add(button("Hacer una oferta", () -> makeOffer(id)));
        }
        facts.getChildren().add(actions);

        HBox hero = new HBox(20, propertyGallery(property, 650, 370), facts);
        hero.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(facts, Priority.ALWAYS);
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

        List<Registro> videos = service.attachments(id, false).stream().filter(file -> file.text("tipo").equals("Video")).toList();
        if (!videos.isEmpty()) {
            VBox media = card(label("Videos de la propiedad", "section-title"));
            for (Registro video : videos)
                media.getChildren().add(secondary(video.text("tipo") + " · Reproducir video", () -> {
                    Path path = service.attachmentPath(id, video.id("id"), false);
                    if (!Files.isRegularFile(path)) throw new IllegalArgumentException("El video ya no está disponible.");
                    getHostServices().showDocument(path.toUri().toString());
                }));
            content.getChildren().add(media);
        }
        if (service.signedIn()
                && (service.role() != 3
                        || property.id("id_propietario") == service.current().id("id_usuario"))) {
            VBox docs = card(label("Documentación privada", "section-title"));
            addFileButtons(docs, id, true);
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
        VBox text = new VBox(2, label(value, "property-detail-fact-value"), label(caption, "property-detail-fact-label"));
        HBox box = new HBox(8, label(icon, "property-detail-fact-icon"), text);
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
        if (!service.signedIn()) { login(); return; }
        if (service.role() != 3) {
            info("Podés vincular un cliente desde Clientes → Intereses.");
            return;
        }
        Formulario form = new Formulario();
        form.area("message", "Consulta", "Me interesa recibir información sobre esta propiedad.");
        dialog("Contactar a la inmobiliaria", form, "Enviar consulta", () -> {
            service.interest(service.current().id("id_usuario"), id, "Interesado", form.value("message"));
            info("Tu consulta quedó registrada. El equipo se pondrá en contacto con vos.");
        });
    }

    private void saveFavorite(int id) {
        if (!service.signedIn()) { login(); return; }
        if (service.role() != 3) { info("Iniciá sesión como cliente para guardar propiedades."); return; }
        service.interest(service.current().id("id_usuario"), id, "Interesado", "Guardada desde favoritos.");
        info("Propiedad agregada a tus intereses.");
    }

    private void addFileButtons(VBox box, int id, boolean docs) {
        List<Registro> files = service.attachments(id, docs);
        if (files.isEmpty())
            box.getChildren().add(label("Todavía no se cargaron archivos.", "subtitle"));
        for (Registro f : files)
            box.getChildren()
                    .add(
                            secondary(
                                    f.text("tipo")
                                            + " · "
                                            + (docs ? f.text("nombre") : "Archivo #" + f.id("id")),
                                    () -> {
                                        Path path = service.attachmentPath(id, f.id("id"), docs);
                                        if (!Files.isRegularFile(path))
                                            throw new IllegalArgumentException(
                                                    "El archivo no está en la carpeta compartida"
                                                            + " configurada.");
                                        getHostServices().showDocument(path.toUri().toString());
                                    }));
    }

    private void upload(int id) {
        Formulario f = new Formulario();
        f.choice(
                "tipo",
                "Tipo de archivo",
                "Imagen",
                "Imagen",
                "Video",
                "Plano",
                "Escritura",
                "Servicio",
                "Impuesto");
        Label filename = label("Ningún archivo seleccionado · Máximo 50 MB", "subtitle");
        Path[] path = {null};
        VBox box =
                card(
                        label("La propiedad debe estar en borrador o rechazada.", "subtitle"),
                        f,
                        filename,
                        secondary(
                                "Seleccionar archivo",
                                () -> {
                                    FileChooser fc = new FileChooser();
                                    fc.setTitle("Adjuntar documentación o multimedia");
                                    java.io.File file = fc.showOpenDialog(stage);
                                    if (file != null) {
                                        path[0] = file.toPath();
                                        filename.setText(file.getName());
                                    }
                                }));
        dialog(
                "Adjuntar archivo",
                box,
                "Guardar archivo",
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
        service.generarAlertasDeSeguimiento();
        if (service.role() == 1) {
            adminDashboard();
            return;
        }
        List<Registro> properties = service.allProperties();
        List<Registro> requests = service.requests();
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
                                        .filter(r -> r.text("estado").equals("Pendiente"))
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
                        "id_publicacion",
                        "ID",
                        "titulo",
                        "Propiedad",
                        "estado",
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
        metrics.getChildren().addAll(
                adminMetric("♙", "Clientes registrados", data.clients(), "Personas en la cartera", "blue"),
                adminMetric("▤", "Solicitudes pendientes", data.pendingRequests(), "Requieren revisión", "gold"),
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
                        button("Ver ficha", () -> propertyDetails(selected(t).id("id_propiedad"))),
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

    private void propertyForm(Registro original) {
        Registro p = original == null ? new Registro() : original;
        boolean edit = original != null;
        page(
                edit ? "Editar propiedad #" + p.id("id_propiedad") : "Nueva propiedad",
                "Guardá la ficha en borrador para adjuntar archivos, o enviala a revisión. Las"
                        + " modificaciones requieren una nueva aprobación.");
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
        VBox offersBox =
                card(
                        label("Operaciones y precios", "section-title"),
                        label(
                                "Activá venta, alquiler o ambas. Cada oferta tiene su propio precio"
                                        + " y moneda.",
                                "subtitle"));
        Map<Integer, CheckBox> enabled = new LinkedHashMap<>();
        Map<Integer, TextField> prices = new HashMap<>();
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
            ComboBox<String> currency =
                    new ComboBox<>(FXCollections.observableArrayList("USD", "ARS"));
            currency.setValue(offer.isEmpty() ? "USD" : offer.text("moneda"));
            enabled.put(id, on);
            prices.put(id, price);
            currencies.put(id, currency);
            offersBox.getChildren().add(row(on, price, currency));
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
                                                    currencies.get(id).getValue()));
                            });
                    int id =
                            service.saveProperty(
                                    edit ? p.id("id_propiedad") : null,
                                    f.values(),
                                    offers,
                                    checked(features),
                                    submit);
                    propertyDetails(id);
                };
        content.getChildren()
                .addAll(
                        card(f),
                        offersBox,
                        card(
                                label("Características adicionales", "section-title"),
                                flow(features.values().toArray(Node[]::new))),
                        flow(
                                secondary("Guardar borrador", () -> save.accept(false)),
                                button("Guardar y enviar a revisión", () -> save.accept(true)),
                                secondary("Cancelar", this::propertyList)));
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
        page(
                "Solicitudes de publicación",
                "Seguí cada envío y su revisión. Los rechazos incluyen el motivo para corregir la"
                        + " ficha.");
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
        dialog(
                (approve ? "Aprobar" : "Rechazar") + " solicitud #" + r.id("id_publicacion"),
                f,
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
        Formulario assign = new Formulario();
        if (service.role() == 1)
            assign.rows(
                    "agent",
                    "Agente asignado",
                    service.agents(),
                    client.id("id_agente_asignado"),
                    true);
        content.getChildren()
                .add(
                        card(
                                label("Roles del cliente", "section-title"),
                                flow(checks.values().toArray(Node[]::new)),
                                service.role() == 1
                                        ? assign
                                        : label(
                                                "Agente asignado: "
                                                        + service.current().text("nombre"),
                                                "subtitle"),
                                button(
                                        "Guardar ficha",
                                        () -> {
                                            Set<String> chosen = new HashSet<>();
                                            checks.forEach(
                                                    (k, c) -> {
                                                        if (c.isSelected()) chosen.add(k);
                                                    });
                                            int agent =
                                                    service.role() == 1
                                                            ? number(assign.value("agent"))
                                                            : client.id("id_agente_asignado");
                                            service.saveClient(
                                                    id, chosen, agent == 0 ? null : agent);
                                            info("Ficha actualizada.");
                                        })));
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
        if (service.role() == 1)
            clientActions.add(secondary("Historial de asignaciones", () -> assignmentHistory(id)));
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
        f.date("fecha", "Fecha", LocalDate.now().plusDays(1).toString());
        f.text("hora", "Hora (HH:mm)", "10:00");
        f.area("notes", "Observaciones", "");
        dialog("Agendar visita", f, "Guardar", () -> {
            service.agendarVisita(client, number(f.value("property")), dateTime(f), f.value("notes"));
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
        f.rows("property", "Propiedad relacionada (opcional)", propertyChoices(false), 0, true);
        f.text("title", "Tarea *", "Contactar al cliente");
        f.date("fecha", "Fecha límite", LocalDate.now().toString());
        f.text("hora", "Hora límite (HH:mm)", "17:00");
        f.area("detail", "Detalle", "");
        dialog("Nuevo recordatorio", f, "Guardar", () -> {
            int prop = number(f.value("property"));
            service.crearTareaSeguimiento(number(f.value("client")), prop == 0 ? null : prop,
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
                service.role() == 3 ? "agente" : "cliente", service.role() == 3 ? "Agente" : "Cliente", "fecha_limite", "Vencimiento", "estado", "Estado", "estado_pago", "Pago");
        HBox reservationActions = new HBox(10);
        if (service.role() == 1) reservationActions.getChildren().addAll(
                button("Aprobar reserva", () -> resolveReservation(selected(reservationTable), true)),
                secondary("Rechazar reserva", () -> resolveReservation(selected(reservationTable), false)));
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

    private void resolveReservation(Registro reservation, boolean approve) {
        service.resolverReserva(reservation.id("id_reserva"), approve);
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
                                                    a.setHeaderText(null);
                                                    if (a.showAndWait().orElse(ButtonType.NO)
                                                            == ButtonType.YES) {
                                                        service.setActive(u.id("id"), false);
                                                        users();
                                                    }
                                                }))));
    }

    private void agents() {
        page("Agentes", "Consultá el equipo, su cartera asignada y el estado de sus cuentas.");
        TableView<Registro> t = table(new PanelAdministradorDAO(service.db).listAgents(),
                "id_usuario", "ID", "agente", "Agente", "email", "Correo", "telefono", "Teléfono",
                "clientes", "Clientes asignados", "propiedades", "Propiedades creadas", "activo", "Activo");
        content.getChildren().addAll(
                row(button("+ Crear agente", () -> newUser(true)), secondary("Ver todos los usuarios", this::users)),
                card(t, flow(
                        button("Ver clientes asignados", () -> {
                            Registro agent = selected(t);
                            page("Cartera de " + agent.text("agente"), "Clientes asignados a este agente.");
                            List<Registro> assigned = service.clients().stream()
                                    .filter(client -> client.id("id_agente_asignado") == agent.id("id_usuario"))
                                    .toList();
                            TableView<Registro> clients = table(assigned, "id", "ID", "etiqueta", "Cliente", "email", "Correo", "telefono", "Teléfono");
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
