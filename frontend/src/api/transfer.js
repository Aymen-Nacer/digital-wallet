import client from './client'

export const postTransfer = (data) => client.post('/transfer', data)
