package ar.edu.nuevohogar.interfaz.autenticacion;

import ar.edu.nuevohogar.interfaz.componentes.Formulario;

/** Construye los formularios de inicio de sesión y registro de clientes. */
public final class FormularioAcceso {
    private FormularioAcceso() {}

    public static Formulario inicioSesion() {
        Formulario formulario = new Formulario(1);
        formulario.text("email", "Correo electrónico", "");
        formulario.password("password", "Contraseña");
        return formulario;
    }

    public static Formulario datosPersonales() {
        Formulario formulario = new Formulario();
        formulario.text("nombre", "Nombre *", "");
        formulario.text("apellido", "Apellido *", "");
        formulario.choice("tipo_documento", "Tipo de documento", "DNI", "DNI", "Pasaporte", "Otro");
        formulario.text("dni", "Número de documento *", "");
        formulario.date("fecha_nacimiento", "Fecha de nacimiento", "");
        formulario.choice("genero", "Género (opcional)", "Prefiero no indicar",
                "Prefiero no indicar", "Femenino", "Masculino", "Otro");
        return formulario;
    }

    public static Formulario datosDeContacto() {
        Formulario formulario = new Formulario();
        formulario.text("email", "Correo electrónico *", "");
        formulario.text("telefono", "Teléfono", "");
        formulario.password("password", "Contraseña * (10 caracteres o más)");
        formulario.password("confirm", "Confirmar contraseña *");
        return formulario;
    }
}
