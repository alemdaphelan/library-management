import csv
import sys

input_file = "C:/Users/Lenovo/OneDrive/Desktop/library-management-imply/library-management/Books.csv"
output_file = "C:/Users/Lenovo/OneDrive/Desktop/library-management-imply/library-management/backend/src/main/resources/data/Books_Cleaned.csv"

with open(input_file, 'r', encoding='utf-8', errors='ignore') as infile, open(output_file, 'w', encoding='utf-8', newline='') as outfile:
    reader = csv.reader(infile, delimiter=',', quotechar='"', escapechar='\\')
    writer = csv.writer(outfile, delimiter=',', quotechar='"', quoting=csv.QUOTE_MINIMAL)
    
    header = next(reader)
    writer.writerow(header)
    
    for line_idx, row in enumerate(reader, start=2):
        if len(row) > 8:
            # If there are extra commas, combine the title parts
            isbn = row[0]
            year_idx = len(row) - 5
            title = ",".join(row[1:year_idx])
            author = row[year_idx]
            year = row[year_idx+1]
            publisher = row[year_idx+2]
            img_s = row[year_idx+3]
            img_m = row[year_idx+4]
            img_l = row[year_idx+5] if len(row) > year_idx+5 else ""
            row = [isbn, title, author, year, publisher, img_s, img_m, img_l]
            
        while len(row) < 8:
            row.append("")
            
        # Ensure year is an integer, if not, set to 0
        try:
            int(row[3])
        except:
            row[3] = "0"
            
        writer.writerow(row[:8])
