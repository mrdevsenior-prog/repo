// Ejecutar manualmente con mongosh en la base de auditoría seleccionada.
// La aplicación de consulta nunca crea índices ni modifica documentos.
const events = db.getCollection('tapp_audit_events');
events.createIndex({ event_timestamp: -1, _id: -1 }, { name: 'query_event_timestamp' });
events.createIndex({ 'event_message.app_code': 1, event_timestamp: -1, _id: -1 }, { name: 'query_application_timestamp' });
events.createIndex({ event_priority: 1, event_timestamp: -1, _id: -1 }, { name: 'query_priority_timestamp' });
events.createIndex({ uid: 1 }, { name: 'query_uid' });
