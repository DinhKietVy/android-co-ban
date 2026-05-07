import { Application } from 'express';
import userRoutes from './user.routes'
import dataRoutes from './data.routes'


function routes(app: Application) {
    app.use('/api/users', userRoutes);
    app.use('/api/data', dataRoutes);
}

export default routes;