import pandas as pd

import numpy as np

import matplotlib.pyplot as plt

import requests
from datetime import datetime, timedelta

from openpyxl import load_workbook

network = 'eth'

pool_address = '0x60594a405d53811d3bc4766596efd80fd545a270' #DAI-WETH Pool

parameters = f'{network}/pools/{pool_address}'

specificity = f'{1}'

#URL with your specifications

url = f'https://api.geckoterminal.com/api/v2/networks/{parameters}/ohlcv/day?aggregate={specificity}'

response = requests.get(url)

data = response.json()

#Turning into pandas dataframe, turning the date value into datetime format, making it the index

data = data['data']['attributes']['ohlcv_list']

df = pd.DataFrame(data, columns=['Date', 'Open', 'Close', 'High', 'Low', 'Volume'])

df['Date'] = pd.to_datetime(df['Date'], unit = 's')

df.set_index('Date', inplace = True)

print(df)

# Creating ANN Model


df['3MA'] = df['Close'].shift(1).rolling(window = 3).mean()

df['10MA'] = df['Close'].shift(1).rolling(window = 10).mean()

df['15MA'] = df['Close'].shift(1).rolling(window = 15).mean()

df['Price_Rise'] = np.where(df['Close'].shift(-1) < df['Close'], 1, 0)

df = df.dropna()

X = df.iloc[:, 5:-1]

Y = df.iloc[:, -1]

#Create X_train, X_test, Y_train, Y_test

split_data = int(len(df)*0.7)

X_train, X_test, Y_train, Y_test = X.iloc[:split_data, :], X.iloc[split_data:, :], Y.iloc[:split_data], Y.iloc[split_data:]

print(df)
# df.to_csv("test.csv", encoding='utf-8')

import tensorflow.python.tools

from tensorflow.keras.models import Sequential

from tensorflow.keras.layers import Dense, Dropout

layer_builder = Sequential()

#Standardize Data

from sklearn.preprocessing import StandardScaler

scaler = StandardScaler()

X_train = scaler.fit_transform(X_train)

X_test = scaler.transform(X_test)

# First input layer

layer_builder.add(Dense(units = 128, kernel_initializer = 'uniform',

activation = 'relu', input_dim = X.shape[1]))

# Second input layer

layer_builder.add(Dense(units = 128, kernel_initializer = 'uniform', activation = 'relu'))

# Output layer

layer_builder.add(Dense(units = 1, kernel_initializer = 'uniform', activation = 'sigmoid'))

# Compile the layers

layer_builder.compile(optimizer = 'adam', loss = 'mean_squared_error', metrics = ['accuracy'])

# We then fit our ANN to our training data, using a ‘batch size’ of 5, which is the number of data points used to compute the error before backpropagation, and 100 ‘epochs’, the number of times the training model will be used on the training set.

# The keras ‘predict’ method generates output predictions given our input data ‘X_test’. We then obtain a binary variable by defining Y_pred as true when it is greater than 0.5, and false when it is less.

#Fit ANN to training set

layer_builder.fit(X_train, Y_train, batch_size = 5, epochs = 100)

Y_pred = layer_builder.predict(X_test)

Y_pred = (Y_pred > 0.5)

#Store predictions back into dataframe

df['Y_Pred'] = np.NaN

df.iloc[(-len(Y_pred)):,-1:] = Y_pred.flatten()

#Fit ANN to training set

layer_builder.fit(X_train, Y_train, batch_size = 5, epochs = 100)

Y_pred = layer_builder.predict(X_test)

Y_pred = (Y_pred > 0.5)

#Store predictions back into dataframe

df['Y_Pred'] = np.nan

df.iloc[(-len(Y_pred)):,-1:] = Y_pred.flatten()

# Strategy Returns

df['Returns'] = 0

# Get the log returns

df['Returns'] = np.log(df['Close']/df['Close'].shift(1))

# Shift so that the returns are in line with the day they were achieved

df['Returns'] = df['Returns'].shift(-1)

df['Strategy Returns'] = 0

df['Strategy Returns'] = np.where(df['Y_Pred'] == True, df['Returns'], - df['Returns'])

df['Cumulative Market Returns'] = np.cumsum(df['Returns'])

df['Cumulative Strategy Returns'] = np.cumsum(df['Strategy Returns'])

# Visualize Returns

plt.figure(figsize=(12,4))

plt.plot(df['Cumulative Strategy Returns'], color = 'Blue', label = 'Strategy Returns')

plt.plot(df['Cumulative Market Returns'], color = 'Green', label = 'Market Returns')

plt.xlabel('Date')

plt.xticks(df.index[::5], rotation = 45)

plt.ylabel('Log-Returns')

plt.legend()

plt.show()