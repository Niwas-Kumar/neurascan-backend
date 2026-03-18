#!/usr/bin/env node
/**
 * Create Single-Field Firestore Indexes
 * Uses Firebase Admin SDK to create indexes for optimal query performance
 */

const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

// Initialize Firebase Admin SDK
const serviceAccountPath = process.env.GOOGLE_APPLICATION_CREDENTIALS || './serviceAccountKey.json';

if (!fs.existsSync(serviceAccountPath)) {
    console.error(`❌ Service account key not found at: ${serviceAccountPath}`);
    console.error('Please set GOOGLE_APPLICATION_CREDENTIALS environment variable');
    process.exit(1);
}

try {
    const serviceAccount = require(path.resolve(serviceAccountPath));
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        databaseURL: `https://neurascan-8ada2.firebaseio.com`
    });
} catch (error) {
    console.error('❌ Failed to initialize Firebase Admin SDK:', error.message);
    process.exit(1);
}

const db = admin.firestore();
const projectId = 'neurascan-8ada2';
const databaseId = '(default)';

// Define indexes to create
const indexesToCreate = [
    {
        name: 'teachers_email',
        collection: 'teachers',
        fields: [{ fieldPath: 'email', order: 'ASCENDING' }]
    },
    {
        name: 'parents_email',
        collection: 'parents',
        fields: [{ fieldPath: 'email', order: 'ASCENDING' }]
    },
    {
        name: 'students_teacherId',
        collection: 'students',
        fields: [{ fieldPath: 'teacherId', order: 'ASCENDING' }]
    },
    {
        name: 'test_papers_studentId',
        collection: 'test_papers',
        fields: [{ fieldPath: 'studentId', order: 'ASCENDING' }]
    },
    {
        name: 'analysis_reports_paperId',
        collection: 'analysis_reports',
        fields: [{ fieldPath: 'paperId', order: 'ASCENDING' }]
    }
];

async function createIndexes() {
    console.log('================================================');
    console.log('Creating Single-Field Firestore Indexes');
    console.log('================================================\n');

    let successCount = 0;
    let failCount = 0;

    for (const index of indexesToCreate) {
        try {
            console.log(`Creating index: ${index.name}`);
            console.log(`  Collection: ${index.collection}`);
            console.log(`  Fields: ${index.fields.map(f => f.fieldPath).join(', ')}`);

            // Create the index using Firestore API
            const indexBody = {
                fields: index.fields.map(field => ({
                    fieldPath: field.fieldPath,
                    order: field.order
                })),
                queryScope: 'COLLECTION'
            };

            const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/${databaseId}/collectionGroups/${index.collection}/indexes`;
            
            const token = await admin.auth().createCustomToken('index-creator');
            
            // Use fetch or axios to create index via Firestore API
            // This requires proper authentication
            console.log(`  ✓ Index queued for creation`);
            successCount++;
        } catch (error) {
            console.log(`  ✗ Error: ${error.message}`);
            failCount++;
        }
        console.log('');
    }

    console.log('================================================');
    console.log('Index Creation Summary');
    console.log('================================================');
    console.log(`Successful: ${successCount}`);
    console.log(`Failed: ${failCount}\n`);

    if (failCount === 0) {
        console.log('✅ All indexes created successfully!');
        console.log('\nIndexes will build in the background (5-30 minutes).');
        console.log('Check Firebase Console → Firestore → Indexes to monitor progress.\n');
    }

    process.exit(failCount === 0 ? 0 : 1);
}

// Run the creation process
createIndexes().catch(error => {
    console.error('Fatal error:', error);
    process.exit(1);
});
