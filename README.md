# Vectura Suite

![Vectura Suite Banner](launcher/src/main/resources/app.png)

Vectura Suite es una solución integral y portable para la transferencia segura de archivos mediante el protocolo SFTP. Esta aplicación unifica un servidor SFTP y un cliente SFTP en un único ejecutable, diseñado para operar en entornos Windows sin requerir la instalación previa de dependencias o entornos de ejecución como Java.

---

## Características Principales

### General
* **Arquitectura Unificada:** Ejecutable único que permite iniciar tanto el módulo de Cliente como el de Servidor desde un selector inicial.
* **Portabilidad:** Empaquetado como aplicación autónoma; no requiere instalación de JRE/JDK ni configuración de variables de entorno en el sistema huésped.
* **Interfaz de Usuario:** Interfaz gráfica desarrollada en JavaFX con tema oscuro para entornos de producción.

### Servidor SFTP
* **Gestión de Usuarios:** Base de datos H2 integrada para la administración de credenciales y asignación de directorios raíz por usuario.
* **Seguridad:** Autenticación mediante hash de contraseñas (BCrypt).
* **Monitoreo:** Panel de control con registro de eventos (logs) y visualización de estado del servicio.
* **Red:** Funcionalidades para la configuración automática de reglas de entrada en el Firewall de Windows y detección de IP local.

### Cliente SFTP
* **Explorador de Archivos:** Vista de doble panel para la gestión simultánea del sistema de archivos local y remoto.
* **Gestión de Transferencias:** Sistema de colas para subidas y descargas con cálculo de métricas de rendimiento en tiempo real.
* **Resolución de Conflictos:** Mecanismos para el manejo de archivos duplicados, incluyendo sobreescritura, renombrado automático y omisión.

---

## Descarga e Instalación

La aplicación se distribuye como un paquete portable.

1.  Acceda a la sección de **Releases** del repositorio:
    [Descargar Última Versión](https://github.com/suspecttaco/Vectura/releases)
2.  Descargue el archivo comprimido (`.zip`) correspondiente a la última versión.
3.  Descomprima el archivo en el directorio de destino.
4.  Ejecute el archivo `VecturaSuite.exe`.

---

## Compilación desde el Código Fuente

Instrucciones para generar el ejecutable a partir del código fuente en un entorno de desarrollo.

### Requisitos
* **Java Development Kit (JDK):** Versión 17 o superior.
* **Apache Maven:** Herramienta de gestión de proyectos configurada en el PATH.
* **Sistema Operativo:** Microsoft Windows (requerido para empaquetar el .exe con jpackage).

### Procedimiento de Construcción

1.  **Clonar el repositorio:**
    ```bash
    git clone [https://github.com/suspecttaco/Vectura.git](https://github.com/suspecttaco/Vectura.git)
    cd Vectura
    ```

2.  **Compilar y empaquetar los módulos:**
    Ejecute el siguiente comando de Maven en la raíz del proyecto para limpiar compilaciones previas, resolver dependencias y generar el archivo JAR unificado (`fat-jar`) del launcher:
    ```bash
    mvn clean install -DskipTests
    ```

3.  **Generar el ejecutable nativo:**
    Una vez finalizada la compilación con éxito, utilice la herramienta `jpackage` (incluida en el JDK) para crear la imagen de la aplicación. Ejecute el siguiente comando en su terminal:

    ```bash
    jpackage ^
      --type app-image ^
      --dest output ^
      --name VecturaSuite ^
      --app-version 1.0.0 ^
      --icon app.ico ^
      --input launcher/target ^
      --main-jar launcher-1.0-SNAPSHOT.jar ^
      --main-class com.vectura.launcher.App ^
      --java-options "-Dfile.encoding=UTF-8"
    ```

4.  **Localización del entregable:**
    El ejecutable y sus dependencias se generarán en el directorio: `output/VecturaSuite/`

---

## Tecnologías Utilizadas

* **Lenguaje:** Java 17
* **Framework UI:** JavaFX / FXML
* **Cliente SFTP:** SSHJ
* **Servidor SFTP:** Apache MINA SSHD
* **Persistencia:** H2 Database Engine
* **Empaquetado:** JPackage

---

## Licencia

Copyright © 2025 Ibhar Gomez.