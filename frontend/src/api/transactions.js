import client from './client'

export const getTransactions = (limit = 20) =>
  client.get('/transactions', { params: { limit } })
