Esta segunda meta foi desenvolvida utilizando Spring Boot. Esta tecnologia permite-nos criar uma aplicação Web robusta, facilitando a gestão de dependências e a configuração automática da aplicação.

A estrutura do projeto segue rigorosamente o padrão de arquitetura MVC (Model-View-Controller), separando a lógica de apresentação da lógica de negócio.

Abaixo explicamos cada componente e onde devem programar cada funcionalidade:

1. Controller
   Responsável por receber os pedidos HTTP (GET/POST) do browser (cliente) e decidir o que fazer com eles.

O que faz: Interceta o pedido (ex: /search?q=termo), chama o Model para obter os dados necessários e escolhe qual a View (página HTML) que deve ser devolvida ao utilizador.

Onde programar:
- src/main/java/com/googol/web/controller/

Exemplo: SearchController.java (classes anotadas com @Controller ).

2. Model (A Lógica e os Dados)
   Representa os dados que a aplicação manipula e a lógica de negócio para os obter. No nosso contexto, o Model atua como a ponte (Cliente RMI) para o Backend da Meta 1.

O que faz: 
Service: Contém a lógica de ligação RMI. É aqui que invocamos métodos remotos como gateway.indexURL() ou gateway.search().
Data Objects: São as classes que transportam a informação entre o servidor e o cliente (ex: resultados da pesquisa com URL, título e citação).

Onde programar:
- Lógica/Serviço: src/main/java/com/googol/web/service/ (ex: GoogolService.java anotado com @Service ).
- Objetos de Dados: src/main/java/gateway/ (ex: PageData.java e IGateway.java).

Nota: As classes em gateway/ devem ser cópias exatas das que estão no servidor da Meta 1.

3. View (A Apresentação)
   É a camada visual da aplicação. Utilizamos Thymeleaf, um motor de templates que permite injetar dados Java diretamente no HTML.

O que faz: Recebe os dados fornecidos pelo Controller (ex: uma lista de resultados) e desenha-os no ecrã do utilizador de forma dinâmica (ex: criando uma tabela com th:each).

Onde programar:
- src/main/resources/templates/

Exemplo: index.html, search_results.html.