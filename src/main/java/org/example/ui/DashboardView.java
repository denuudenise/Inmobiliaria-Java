package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.dao.UsuarioDao;

public class DashboardView {
    private final UsuarioDao.Usuario usuario;
    private final Runnable onLogout;

    public DashboardView(UsuarioDao.Usuario usuario, Runnable onLogout) {
        this.usuario = usuario;
        this.onLogout = onLogout;
    }

    public Parent createView() {
        String role = usuario.rol() == null ? "" : usuario.rol().trim().toLowerCase();
        if (role.equals("administrador")) return staffDashboard("Panel de administración", "Administración", "Revisá las publicaciones y gestioná el sistema.");
        if (role.equals("agente")) return staffDashboard("Panel del agente", "Agente inmobiliario", "Gestioná tus propiedades, clientes y solicitudes.");
        if (role.equals("cliente")) return clientDashboard();
        return accessDenied();
    }

    private Parent accessDenied() {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));
        content.getStyleClass().add("dashboard-page");
        Label title = new Label("No se reconoce el rol de esta cuenta"); title.getStyleClass().add("form-title");
        Label copy = new Label("Pedile al administrador que revise el rol asignado en la base de datos."); copy.getStyleClass().add("muted");
        Button logout = new Button("Volver al inicio de sesión"); logout.getStyleClass().add("primary-button"); logout.setOnAction(e -> onLogout.run());
        content.getChildren().addAll(title, copy, logout);
        return content;
    }

    private Parent staffDashboard(String title, String role, String copy) {
        BorderPane root = new BorderPane(); root.getStyleClass().add("dashboard-page");
        VBox sidebar = new VBox(12); sidebar.getStyleClass().add("dashboard-sidebar");
        sidebar.setPadding(new Insets(26, 18, 26, 18)); sidebar.setPrefWidth(245);
        Label brand = new Label("⌂  Nuevo Hogar"); brand.getStyleClass().add("sidebar-brand");
        Label roleText = new Label(role); roleText.getStyleClass().add("sidebar-role");
        VBox menu = new VBox(8);
        String[] options = role.equals("Administración")
                ? new String[]{"Inicio", "Clientes", "Propiedades", "Solicitudes", "Ventas y alquileres", "Agentes", "Usuarios", "Reportes", "Configuración"}
                : new String[]{"Inicio", "Mis propiedades", "Clientes", "Solicitudes de carga", "Notificaciones", "Reportes", "Configuración"};
        for (int i = 0; i < options.length; i++) {
            Button item = new Button((i == 0 ? "●  " : "○  ") + options[i]);
            item.getStyleClass().add(i == 0 ? "nav-item-selected" : "nav-item");
            item.setMaxWidth(Double.MAX_VALUE); menu.getChildren().add(item);
        }
        VBox spacer = new VBox(); VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        Label name = new Label(usuario.nombreCompleto()); name.getStyleClass().add("sidebar-user");
        Button logout = new Button("Cerrar sesión"); logout.getStyleClass().add("nav-item"); logout.setMaxWidth(Double.MAX_VALUE); logout.setOnAction(e -> onLogout.run());
        sidebar.getChildren().addAll(brand, roleText, menu, spacer, name, logout);
        root.setLeft(sidebar);

        VBox content = new VBox(22); content.getStyleClass().add("dashboard-content");
        content.setPadding(new Insets(34));
        HBox top = new HBox(); top.setAlignment(Pos.CENTER_RIGHT);
        Label profile = new Label(usuario.nombreCompleto() + "  ·  " + role); profile.getStyleClass().add("muted"); top.getChildren().add(profile);
        Label heading = new Label(title); heading.getStyleClass().add("form-title");
        Label welcome = new Label("Hola, " + usuario.nombre() + ". " + copy); welcome.getStyleClass().add("muted");
        VBox notice = new VBox(10); notice.getStyleClass().add("content-card"); notice.setPadding(new Insets(24));
        Label noticeTitle = new Label("Sesión iniciada"); noticeTitle.getStyleClass().add("card-title");
        Label noticeCopy = new Label("Tu cuenta ingresó con el rol " + role + ". La navegación y los permisos se asignan según el rol guardado en la base de datos.");
        noticeCopy.setWrapText(true); noticeCopy.getStyleClass().add("muted");
        notice.getChildren().addAll(noticeTitle, noticeCopy);
        content.getChildren().addAll(top, heading, welcome, notice);
        root.setCenter(content);
        return root;
    }

    private Parent clientDashboard() {
        BorderPane root = new BorderPane(); root.getStyleClass().add("dashboard-page");
        VBox page = new VBox(25); page.setPadding(new Insets(24, 56, 48, 56));
        HBox nav = new HBox(26); nav.setAlignment(Pos.CENTER_LEFT); nav.getStyleClass().add("client-nav");
        Label brand = new Label("⌂  Nuevo Hogar"); brand.getStyleClass().add("sidebar-brand");
        Label home = new Label("Inicio"); home.getStyleClass().add("client-nav-active");
        Label buy = new Label("Comprar"); buy.getStyleClass().add("client-nav-label");
        Label rent = new Label("Alquilar"); rent.getStyleClass().add("client-nav-label");
        Label publish = new Label("Publicar propiedad"); publish.getStyleClass().add("client-nav-label");
        Label profile = new Label(usuario.nombreCompleto()); profile.getStyleClass().add("client-nav-label");
        Button logout = new Button("Cerrar sesión"); logout.getStyleClass().add("primary-button"); logout.setOnAction(e -> onLogout.run());
        HBox spacer = new HBox(); HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        nav.getChildren().addAll(brand, home, buy, rent, publish, spacer, profile, logout);
        VBox hero = new VBox(12); hero.getStyleClass().add("client-hero"); hero.setPadding(new Insets(44));
        Label title = new Label("Encontrá la propiedad\nque estás buscando"); title.getStyleClass().add("hero-title");
        Label intro = new Label("Explorá oportunidades y recibí asesoramiento personalizado."); intro.getStyleClass().add("muted");
        Button explore = new Button("Explorar propiedades"); explore.getStyleClass().add("primary-button");
        hero.getChildren().addAll(title, intro, explore);
        Label welcome = new Label("Hola, " + usuario.nombre() + ". Tu cuenta ya está lista para usar."); welcome.getStyleClass().add("form-title");
        Label note = new Label("Desde acá vas a poder guardar búsquedas, contactar a la inmobiliaria y consultar el estado de tus solicitudes."); note.setWrapText(true); note.getStyleClass().add("muted");
        VBox card = new VBox(10, welcome, note); card.getStyleClass().add("content-card"); card.setPadding(new Insets(22));
        page.getChildren().addAll(nav, hero, card); root.setCenter(page);
        return root;
    }
}
