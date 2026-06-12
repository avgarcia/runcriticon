# Referencia del Modelo C4

## 1. Diagrama de contexto del sistema

- **Alcance**: Empresa / Sistema de sistemas.
- **Elementos**: Personas (actores), Sistemas de software (propios y externos).
- **Objetivo**: Visión general. ¿Quién lo utiliza? ¿Con qué se integra?
- **Audiencia**: Todos (negocio, PM, desarrollo).

## 2. Diagrama de contenedores

- **Alcance**: Sistema único.
- **Elementos**: Contenedores (aplicación web, aplicación móvil, API, base de datos, almacenamiento de ficheros, microservicio).
- **No es Docker**: "Contenedor" = unidad desplegable (p. ej., archivo WAR, JAR, SPA).
- **Objetivo**: Decisiones de stack tecnológico. ¿Cómo se comunican los contenedores?
- **Audiencia**: Técnicos (arquitectos, desarrolladores, operaciones).

## 3. Diagrama de componentes

- **Alcance**: Contenedor único.
- **Elementos**: Componentes (Controlador, Servicio, Repositorio), Módulos.
- **Objetivo**: Organización del código y dependencias.
- **Audiencia**: Desarrolladores.

## 4. Diagrama de código (opcional)

- **Alcance**: Componente único.
- **Elementos**: Clases, Interfaces.
- **Objetivo**: Detalles de implementación. Normalmente generado (p. ej., ERD).
