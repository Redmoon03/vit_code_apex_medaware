from flask_sqlalchemy import SQLAlchemy
from sqlalchemy_serializer import SerializerMixin

db = SQLAlchemy()

class User(db.Model, SerializerMixin):
    __tablename__ = "users"
    uid = db.Column(db.Integer, primary_key=True)
    age = db.Column(db.Integer)
    gender = db.Column(db.String(5))
    height = db.Column(db.Integer)
    weight = db.Column(db.Integer)
    email = db.Column(db.Text)
    password = db.Column(db.Text)
    def to_dict(self):
        return { 
            "uid": self.uid,
            "age": self.age,
            "gender": self.gender,
            "height": self.height,
            "weight": self.weight,
            "email": self.email,
            "password": self.password
        }

class Reminder(db.Model, SerializerMixin):
    __tablename__ = "reminders"
    rid = db.Column(db.Integer, primary_key=True)
    uid = db.Column(db.Integer)
    rtime = db.Column(db.Time)
    def to_dict(self):
        return {
            "rid": self.rid,
            "uid": self.uid,
            "rtime": self.rtime
        }

class Medicine_Reminder(db.Model, SerializerMixin):
    __tablename__ = "medicine_reminder"
    mid = db.Column(db.Integer, primary_key=True)
    mname = db.Column(db.Text)
    rid = db.Column(db.Integer)
    dose_qty = db.Column(db.Integer)
    total_qty = db.Column(db.Integer)
    def to_dict(self):
        return {
            "mid": self.mid,
            "mname": self.mname,
            "rid": self.rid,
            "dose_qty": self.dose_qty,
            "total_qty": self.total_qty
        }

class Reminder_Log(db.Model, SerializerMixin):
    __tablename__ = "reminder_log"
    logid = db.Column(db.Integer, primary_key=True)
    status = db.Column(db.String(15))
    rid = db.Column(db.Integer)
    uid = db.Column(db.Integer)
    date = db.Column(db.Date)  # ✅ Corrected
    def to_dict(self):
        return {
            "logid": self.logid,
            "status": self.status,
            "rid": self.rid,
            "uid": self.uid,
            "date": self.date
        }