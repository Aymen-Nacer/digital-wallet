import client from './client'

export const getWallet = (id) => client.get(`/wallets/${id}`)

export const createWallet = (data) => client.post('/wallets', data)

export const depositToWallet = (id, amount) =>
  client.post(`/wallets/${id}/deposit`, null, { params: { amount } })
