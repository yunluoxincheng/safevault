\# Java Project Rules



\## Framework

\- Use Spring Boot 3

\- Use Maven for dependency management



\## Architecture

Follow layered architecture:



Controller -> Service -> Repository



Rules:

\- Controllers handle HTTP requests only

\- Services contain business logic

\- Repositories access the database



\## DTO

\- Always use DTO for API input/output

\- Never expose entity objects in controllers



\## Entities

\- Use JPA / Hibernate

\- Use Lombok for boilerplate code



\## Naming conventions

Controllers: \*Controller

Services: \*Service

Repositories: \*Repository

DTO: \*DTO



\## Error handling

\- Use global exception handler

\- Use ResponseEntity for API responses



\## Testing

\- Use JUnit 5

\- Write unit tests for service layer

