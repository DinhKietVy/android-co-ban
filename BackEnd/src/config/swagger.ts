import swaggerJsdoc from "swagger-jsdoc";
import swaggerUi from "swagger-ui-express";
import { Express, Request, Response, NextFunction } from "express";

const baseOptions: swaggerJsdoc.Options = {
  definition: {
    openapi: "3.0.0",
    info: {
      title: "Backend API",
      version: "1.0.0",
      description: "API Docs",
    },
    components: {
      securitySchemes: {
        bearerAuth: {
          type: "http",
          scheme: "bearer",
          bearerFormat: "JWT",
        },
      },
    },
    security: [
      {
        bearerAuth: [],
      },
    ],
  },

  apis: ["./src/routes/**/*.ts"],
};

export const setupSwagger = (app: Express) => {
  // serve static assets của Swagger UI
  app.use("/api-docs", swaggerUi.serve);

  // Tạo spec động theo từng request để lấy đúng host (localhost hay ngrok)
  app.get("/api-docs", (req: Request, res: Response, next: NextFunction) => {
    const protocol = req.headers['x-forwarded-proto'] || req.protocol;
    const host = req.headers['x-forwarded-host'] || req.get('host');
    const serverUrl = `${protocol}://${host}`;

    const dynamicOptions: swaggerJsdoc.Options = {
      ...baseOptions,
      definition: {
        ...baseOptions.definition,
        openapi: "3.0.0",
        info: {
          title: "Backend API",
          version: "1.0.0",
          description: "API Docs",
        },
        servers: [{ url: serverUrl }],
      },
    };

    const swaggerSpec = swaggerJsdoc(dynamicOptions);
    return swaggerUi.setup(swaggerSpec, {
      swaggerOptions: {
        persistAuthorization: true,
      },
    })(req, res, next);
  });
};