from pymongo import MongoClient

client = MongoClient('mongodb://admin:admin_password@localhost:27017/') # Wait, the app uses 'mongo_admin' maybe? Let's check application.yml
