module.exports = {
  'football-analysis-api': {
    output: {
      mode: 'tags-split', // A Swagger tagek alapján külön fájlokba bontja a kódokat (pl. UserController, MatchController)
      target: 'src/api/generated', // Ide fogja legenerálni a fájlokat
      schemas: 'src/api/generated/model', // Ide kerülnek a TypeScript típusok/interfészek
      client: 'react-query', // Automatikusan TanStack Query hookokat generál
      mock: false, // (Opcionális) Msw mockokat is tud generálni teszteléshez, ha később kellene
    },
    input: {
      // Itt hivatkozunk a Spring Boot backend által kitett JSON-re
      target: 'http://localhost:8080/v3/api-docs',
    },
  },
};