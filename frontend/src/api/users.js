import client from './client'

export const getAllUsers = () => client.get('/users')

export const createUser = (data) => client.post('/users', data)
