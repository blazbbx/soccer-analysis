module.exports = {
  'football-analysis-api': {
    output: {
      mode: 'tags-split', 
      target: 'src/api/generated', 
      schemas: 'src/api/generated/model', 
      client: 'react-query',
      mock: false,
      override: {
        fetch: {
          includeHttpResponseReturnType: false,
        },
        mutator: {
          path: './src/api/axiosInstance.ts',
          name: 'customInstance',
        },
       operations: {
          getAllTeams: {
            response: {
              current: 'TeamResponse[]',
            },
          },
          createInvite: {
            response: {
              current: 'InviteLinkResponse'
            }
          },
          getMatch: {
            response:{
              current: 'MatchResponse'
            }
          }
        },
      }
    },
    input: {
      target: 'http://localhost:8080/v3/api-docs',
    },
  },
};