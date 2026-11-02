import { request, queryString } from './client.js'

export function updateProfile(payload) {
  return request('/users/me', { method: 'PUT', body: JSON.stringify(payload) })
}

export function getUsers(query, signal) {
  return request('/admin/users?' + queryString(query), { signal })
}

export function getRoles(signal) {
  return request('/admin/roles', { signal })
}

export function updateUserStatus(id, status) {
  return request('/admin/users/' + encodeURIComponent(String(id)) + '/status', {
    method: 'PUT',
    body: JSON.stringify({ status }),
  })
}

export function updateUserRoles(id, roleIds) {
  return request('/admin/users/' + encodeURIComponent(String(id)) + '/roles', {
    method: 'PUT',
    body: JSON.stringify({ userId: String(id), roleIds }),
  })
}
