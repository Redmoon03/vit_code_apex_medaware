import pywhatkit as kit

def send_message(number, medicine):
    # Format: +919876543210
    message = f"🚨 MedAware Alert: Patient missed their dose of {medicine}!"
    
    # Sends message instantly (opens browser tab, types, and sends)
    kit.sendwhatmsg_instantly(number, message, wait_time=15, tab_close=True)