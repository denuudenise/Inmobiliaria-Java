<?php
declare(strict_types=1);

// Copiar este archivo como /Inmobiliaria/api/media.php en el hosting.
require_once dirname(__DIR__) . '/sistema/Nucleo/inicio.php';

header('Cache-Control: no-store');

function media_json(int $status, array $payload): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function media_user(): array
{
    $session = current_user();
    if (!$session) media_json(401, ['error' => 'Iniciá sesión para continuar.']);
    $q = Database::connection()->prepare(
        'SELECT u.activo,r.nombre AS rol FROM usuarios u JOIN roles r ON r.id_rol=u.id_rol WHERE u.id_usuario=?'
    );
    $q->execute([(int)$session['id']]);
    $current = $q->fetch();
    if (!$current || !(bool)$current['activo']) {
        unset($_SESSION['user']);
        media_json(401, ['error' => 'La cuenta está inactiva.']);
    }
    $_SESSION['user']['role'] = $current['rol'];
    return $_SESSION['user'];
}

function media_check_csrf(): void
{
    $sent = (string)($_POST['csrf_token'] ?? '');
    $saved = (string)($_SESSION['media_api_csrf'] ?? '');
    if ($saved === '' || !hash_equals($saved, $sent))
        media_json(403, ['error' => 'La sesión venció. Cerrá e iniciá sesión de nuevo.']);
}

function media_password_matches(string $password, string $hash): bool
{
    if (password_verify($password, $hash)) return true;
    // La app Java también crea hashes PBKDF2-SHA256 con este formato.
    $parts = explode('$', $hash);
    if (count($parts) !== 4 || $parts[0] !== 'pbkdf2') return false;
    $iterations = filter_var($parts[1], FILTER_VALIDATE_INT);
    $salt = base64_decode($parts[2], true);
    $expected = base64_decode($parts[3], true);
    if (!$iterations || $iterations < 100000 || $iterations > 2000000 || $salt === false || $expected === false) return false;
    return hash_equals($expected, hash_pbkdf2('sha256', $password, $salt, $iterations, 32, true));
}

function media_context(PDO $db, string $target, int $id, array $user): array
{
    if ($target === 'propiedad') {
        $q = $db->prepare('SELECT p.id_propietario,p.id_agente_creador,p.id_estado_verificacion,c.id_agente_asignado '
            . 'FROM propiedades p LEFT JOIN clientes c ON c.id_cliente=p.id_propietario WHERE p.id_propiedad=?');
        $q->execute([$id]);
        $row = $q->fetch();
        if (!$row) throw new RuntimeException('La propiedad no existe.');
        $allowed = $user['role'] === 'Administrador'
            || ($user['role'] === 'Cliente' && (int)$row['id_propietario'] === (int)$user['id'])
            || ($user['role'] === 'Agente' && ((int)$row['id_agente_creador'] === (int)$user['id']
                || (int)$row['id_agente_asignado'] === (int)$user['id']));
        if (!$allowed) throw new RuntimeException('No tenés acceso a esta propiedad.');
        return $row;
    }
    if ($target === 'solicitud') {
        $q = $db->prepare('SELECT id_cliente,id_agente,id_estado_verificacion FROM solicitudes_publicacion WHERE id_solicitud=?');
        $q->execute([$id]);
        $row = $q->fetch();
        if (!$row) throw new RuntimeException('La solicitud no existe.');
        $allowed = $user['role'] === 'Administrador'
            || ($user['role'] === 'Cliente' && (int)$row['id_cliente'] === (int)$user['id'])
            || ($user['role'] === 'Agente' && (int)$row['id_agente'] === (int)$user['id']);
        if (!$allowed) throw new RuntimeException('No tenés acceso a esta solicitud.');
        if ((int)$row['id_estado_verificacion'] !== 2)
            throw new RuntimeException('Solo se adjuntan archivos a solicitudes pendientes.');
        return $row;
    }
    throw new RuntimeException('Tipo de ficha inválido.');
}

function media_extension(string $mime, string $type): ?string
{
    $allowed = match ($type) {
        'Imagen' => ['image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp'],
        'Video' => ['video/mp4' => 'mp4'],
        'Plano', 'Escritura', 'Impuesto', 'Servicio', 'Reglamento', 'Otra'
            => ['application/pdf' => 'pdf', 'image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp'],
        default => [],
    };
    return $allowed[$mime] ?? null;
}

$action = (string)($_GET['action'] ?? $_POST['action'] ?? '');

if ($action === 'login') {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') media_json(405, ['error' => 'Método no permitido.']);
    $data = json_decode((string)file_get_contents('php://input'), true);
    $email = is_array($data) ? trim((string)($data['email'] ?? '')) : '';
    $password = is_array($data) ? (string)($data['password'] ?? '') : '';
    try {
        $user = (new UsuarioDAO(Database::connection()))->findByEmail($email);
        if (!$user || !(bool)$user['activo'] || !media_password_matches($password, $user['password_hash']))
            media_json(401, ['error' => 'Correo o contraseña incorrectos.']);
        session_regenerate_id(true);
        $_SESSION['user'] = [
            'id' => (int)$user['id_usuario'], 'name' => $user['nombre'],
            'surname' => $user['apellido'], 'dni' => $user['dni'],
            'phone' => $user['telefono'], 'email' => $user['email'], 'role' => $user['rol'],
        ];
        $_SESSION['media_api_csrf'] = bin2hex(random_bytes(32));
        media_json(200, ['csrf_token' => $_SESSION['media_api_csrf']]);
    } catch (PDOException $error) {
        error_log('Media API login: ' . $error->getMessage());
        media_json(503, ['error' => 'El servidor no pudo validar la cuenta.']);
    }
}

if ($action === 'logout') {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') media_json(405, ['error' => 'Método no permitido.']);
    media_check_csrf();
    unset($_SESSION['user'], $_SESSION['media_api_csrf']);
    session_regenerate_id(true);
    media_json(200, ['ok' => true]);
}

if ($action === 'download') {
    if ($_SERVER['REQUEST_METHOD'] !== 'GET') media_json(405, ['error' => 'Método no permitido.']);
    $user = media_user();
    $documentId = (int)($_GET['id'] ?? 0);
    if ($documentId < 1) media_json(400, ['error' => 'Documento inválido.']);
    $db = Database::connection();
    $q = $db->prepare('SELECT d.*,p.id_propietario,p.id_agente_creador,c.id_agente_asignado,'
        . 's.id_cliente AS solicitante,s.id_agente AS agente_solicitud '
        . 'FROM documentos d LEFT JOIN propiedades p ON p.id_propiedad=d.id_propiedad '
        . 'LEFT JOIN clientes c ON c.id_cliente=p.id_propietario '
        . 'LEFT JOIN solicitudes_publicacion s ON s.id_solicitud=d.id_solicitud WHERE d.id_documento=?');
    $q->execute([$documentId]);
    $doc = $q->fetch();
    if (!$doc) media_json(404, ['error' => 'Documento no encontrado.']);
    $isProperty = $doc['id_propiedad'] !== null;
    $owner = (int)($isProperty ? $doc['id_propietario'] : $doc['solicitante']);
    $creator = (int)($isProperty ? $doc['id_agente_creador'] : $doc['agente_solicitud']);
    $assigned = (int)($isProperty ? $doc['id_agente_asignado'] : $doc['agente_solicitud']);
    $allowed = $user['role'] === 'Administrador'
        || ($user['role'] === 'Cliente' && $owner === (int)$user['id'])
        || ($user['role'] === 'Agente' && ((int)$user['id'] === $creator || (int)$user['id'] === $assigned));
    if (!$allowed) media_json(403, ['error' => 'No tenés permiso para ver este documento.']);
    $filename = basename((string)$doc['archivo']);
    if (str_contains($filename, '..') || !preg_match('/\.(pdf|jpe?g|png|webp)$/i', $filename))
        media_json(404, ['error' => 'El documento no está en el almacenamiento del servidor.']);
    $path = dirname(__DIR__) . '/sistema/privados/' . $filename;
    if (!is_file($path)) media_json(404, ['error' => 'El archivo privado no está disponible.']);
    header('Content-Type: ' . ((new finfo(FILEINFO_MIME_TYPE))->file($path) ?: 'application/octet-stream'));
    header('Content-Length: ' . (string)filesize($path));
    header('Content-Disposition: attachment; filename="documento-' . $documentId . '.' . pathinfo($filename, PATHINFO_EXTENSION) . '"');
    readfile($path);
    exit;
}

if ($action !== 'upload' || $_SERVER['REQUEST_METHOD'] !== 'POST')
    media_json(404, ['error' => 'Acción no encontrada.']);

$user = media_user();
media_check_csrf();
$target = (string)($_POST['target'] ?? '');
$id = (int)($_POST['id'] ?? 0);
$type = (string)($_POST['type'] ?? '');
$upload = $_FILES['file'] ?? null;
if ($id < 1 || !$upload || !is_uploaded_file((string)$upload['tmp_name']))
    media_json(400, ['error' => 'Falta seleccionar un archivo válido.']);

$isMedia = in_array($type, ['Imagen', 'Video'], true);
$isDocument = in_array($type, ['Plano', 'Escritura', 'Impuesto', 'Servicio', 'Reglamento', 'Otra'], true);
if (!$isMedia && !$isDocument) media_json(400, ['error' => 'Tipo de archivo inválido.']);
$limit = $target === 'solicitud' ? 2 * 1024 * 1024 : 2 * 1024 * 1024;
if ((int)$upload['error'] !== UPLOAD_ERR_OK || (int)$upload['size'] < 1 || (int)$upload['size'] > $limit)
    media_json(400, ['error' => 'Cada archivo debe pesar hasta 2 MB.']);
$mime = (new finfo(FILEINFO_MIME_TYPE))->file((string)$upload['tmp_name']);
$extension = media_extension((string)$mime, $type);
if (!$extension) media_json(400, ['error' => 'El formato del archivo no está permitido.']);

$db = Database::connection();
$db->beginTransaction();
$path = null;
try {
    $context = media_context($db, $target, $id, $user);
    if ($target === 'propiedad') {
        $status = (int)$context['id_estado_verificacion'];
        if ($isMedia && !in_array($status, [1, 4], true))
            throw new RuntimeException('Guardá la propiedad como borrador antes de cargar fotos o videos.');
        if ($isDocument && !in_array($status, [1, 4], true)
            && !($user['role'] !== 'Cliente' && $status === 2))
            throw new RuntimeException('Guardá la propiedad como borrador antes de adjuntar documentación.');
    }
    if ($target === 'solicitud') {
        $table = $isMedia ? 'solicitud_multimedia' : 'documentos';
        $field = $isMedia ? 'id_solicitud' : 'id_solicitud';
        $q = $db->prepare("SELECT COUNT(*) FROM $table WHERE $field=?");
        $q->execute([$id]);
        if ((int)$q->fetchColumn() >= 8)
            throw new RuntimeException($isMedia ? 'La solicitud admite hasta 8 fotos o videos.' : 'La solicitud admite hasta 8 documentos.');
    }

    // Las imágenes usan el formato de Archivos::remove del sitio; documentos mantienen 48 hex.
    $filename = bin2hex(random_bytes($isDocument ? 24 : 16)) . '.' . $extension;
    $root = dirname(__DIR__);
    $folder = $isDocument ? $root . '/sistema/privados'
        : $root . '/archivos/' . ($target === 'solicitud' ? 'solicitudes' : 'propiedades');
    if (!is_dir($folder) && !mkdir($folder, $isDocument ? 0700 : 0750, true))
        throw new RuntimeException('No se pudo preparar la carpeta de destino.');
    $path = $folder . '/' . $filename;
    if (!move_uploaded_file((string)$upload['tmp_name'], $path))
        throw new RuntimeException('El hosting no pudo guardar el archivo.');

    $original = mb_substr(basename(str_replace('\\', '/', (string)$upload['name'])), 0, 255);
    if ($isMedia && $target === 'propiedad') {
        $stored = '/Inmobiliaria/archivos/propiedades/' . $filename;
        $q = $db->prepare('INSERT INTO propiedad_multimedia(id_propiedad,url_archivo,tipo,orden) VALUES(?,?,?,1)');
        $q->execute([$id, $stored, $type]);
        $recordId = (int)$db->lastInsertId();
        $entity = 'propiedades';
    } elseif ($isMedia) {
        $stored = '/Inmobiliaria/archivos/solicitudes/' . $filename;
        $q = $db->prepare('INSERT INTO solicitud_multimedia(id_solicitud,url_archivo,tipo) VALUES(?,?,?)');
        $q->execute([$id, $stored, $type]);
        $recordId = (int)$db->lastInsertId();
        $entity = 'solicitudes_publicacion';
    } else {
        $q = $db->prepare('INSERT INTO documentos(id_propiedad,id_solicitud,tipo,nombre,archivo,id_usuario) VALUES(?,?,?,?,?,?)');
        $q->execute([$target === 'propiedad' ? $id : null, $target === 'solicitud' ? $id : null,
            $type, $original, $filename, (int)$user['id']]);
        $recordId = (int)$db->lastInsertId();
        $entity = 'documentos';
    }
    (new AuditoriaDAO($db))->record((int)$user['id'], 'Subir archivo desde app', $entity, $recordId, $type . ': ' . $original);
    $db->commit();
    media_json(201, ['ok' => true, 'id' => $recordId,
        'url' => $isMedia ? $stored : null]);
} catch (Throwable $error) {
    if ($db->inTransaction()) $db->rollBack();
    if ($path !== null && is_file($path)) unlink($path);
    if ($error instanceof PDOException) {
        error_log('Media API upload: ' . $error->getMessage());
        media_json(500, ['error' => 'No se pudo registrar el archivo en la base de datos.']);
    }
    media_json(400, ['error' => $error->getMessage()]);
}
