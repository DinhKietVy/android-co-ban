import { Application } from 'express';
import userRoutes from './user.routes'
import dataRoutes from './data.routes'
import binRoutes from './bin.routes'
import publicLinkRoutes from './public-link.routes'

function routes(app: Application) {
    app.use('/api/users', userRoutes);
    app.use('/api/data', dataRoutes);
    app.use('/api/bin', binRoutes);
    app.use('/api/public-link', publicLinkRoutes);
}

export default routes;