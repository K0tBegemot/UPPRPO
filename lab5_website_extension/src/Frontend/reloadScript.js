function reloadPage(){
    window.location.reload();
}

function deleteErrorMessage(table){
    let thead = table.getElementsByTagName("thead")[0];
    for(let i = 0; i < thead.rows.length - 1; i++)
    {
        thead.deleteRow(i);
    }
}

function loadMainPage(){
    let table = document.getElementById("logList");
    deleteErrorMessage(table);
    let httpRequest = new XMLHttpRequest();
    if(!httpRequest)
    {
        addErrorMessage(table, "Can't connect to server. Try again");
    }else{
        httpRequest.open("GET", `/get_max_index`);
        httpRequest.responseType = "json";
        httpRequest.send();
        httpRequest.onreadystatechange = function (){
            if(httpRequest.readyState === XMLHttpRequest.DONE)
            {
                let maxIndexObject = httpRequest.response;
                let maxIndex = maxIndexObject["maxIndex"];
                if(typeof maxIndex == "number")
                {
                    loadPrevChunk(maxIndex);
                }else{
                    addErrorMessage(table, "FATAL SERVER ERROR. Data isn't available");
                }
            }
        }
    }
}

function addErrorMessage(table, message){
    let thead = table.getElementsByTagName("thead")[0];
    if(thead.rows.length !== 1) {
        deleteErrorMessage(table);
    }
    let newRow = thead.insertRow(0);
    newRow.id = "logList";
    let newCell1 = newRow.insertCell(0).outerHTML = `<th class="errorMessageColorField"></th>`;
    let newCell2 = newRow.insertCell(1).outerHTML = `<th class="secondColumn">${message}</th>`;
}

function loadNextChunk(index){
    let table = document.getElementById("logList");
    deleteErrorMessage(table);
    let tbody = table.getElementsByTagName("tbody")[0];
    let thead = table.getElementsByTagName("thead")[0];
    let nextIndex;
    if(index == null)
    {
        nextIndex = Number(tbody.getElementsByTagName("tr")[tbody.rows.length - 1].getElementsByTagName("td")[0].innerText) + 1;
    }else{
        nextIndex = index;
    }
    let httpRequest = new XMLHttpRequest();
    if(!httpRequest)
    {
        addErrorMessage(table, "Can't connect to server. Try again");
    }else{
        httpRequest.open("GET", `/get_next_chunk?index=${nextIndex}`);
        httpRequest.responseType = "json";
        httpRequest.send();
        httpRequest.onreadystatechange = function (){
            if (httpRequest.readyState === XMLHttpRequest.DONE) {
                let array = httpRequest.response["pairArray"];
                if (Array.isArray(array)) {
                    deleteErrorMessage(table);
                    for (let i = 0; i < array.length; i++) {
                        let newRowRemote = array[i];
                        let newRow = tbody.insertRow(tbody.rows.length);
                        let newCell1 = newRow.insertCell(0);
                        newCell1.className = "firstColumn";
                        let newCell2 = newRow.insertCell(1);
                        newCell2.className = "secondColumn";
                        let newTextIndex = document.createTextNode(newRowRemote["row_id"]);
                        let newText = document.createTextNode(newRowRemote["text"]);
                        newCell1.appendChild(newTextIndex)
                        newCell2.appendChild(newText);
                    }
                } else {
                    if(array != null)
                    {
                        addErrorMessage(table, "Can't load next chunk. Server isn't available. Try again");
                    }else{
                        addErrorMessage(table, "There is no new log data. You need to wait!");
                    }
                }
            }
        }
    }
}

function loadPrevChunk(index){
    let table = document.getElementById("logList");
    deleteErrorMessage(table);
    let tbody = table.getElementsByTagName("tbody")[0];
    let thead = table.getElementsByTagName("thead")[0];
    let prevIndex;
    if(index == null)
    {
        prevIndex = Number(tbody.getElementsByTagName("tr")[0].getElementsByTagName("td")[0].innerText) - 1;
    }else{
        prevIndex = index;
    }
    let httpRequest = new XMLHttpRequest();
    if(!httpRequest)
    {
        addErrorMessage(table, "Can't connect to server. Try again");
    }else{
        httpRequest.open("GET", `/get_prev_chunk?index=${prevIndex}`);
        httpRequest.responseType = "json";
        httpRequest.send();
        httpRequest.onreadystatechange = function (){
            if (httpRequest.readyState === XMLHttpRequest.DONE) {
                let array = httpRequest.response["pairArray"];
                if (Array.isArray(array)) {
                    deleteErrorMessage(table);
                    for (let i = array.length - 1; i > -1; i--) {
                        let newRowRemote = array[i];
                        let newRow = tbody.insertRow(0);
                        let newCell1 = newRow.insertCell(0);
                        newCell1.className = "firstColumn";
                        let newCell2 = newRow.insertCell(1);
                        newCell2.className = "secondColumn";
                        let newTextIndex = document.createTextNode(newRowRemote["row_id"]);
                        let newText = document.createTextNode(newRowRemote["text"]);
                        newCell1.appendChild(newTextIndex);
                        newCell2.appendChild(newText);
                    }
                } else {
                    if(array != null)
                    {
                        addErrorMessage(table, "Can't load next chunk. Server isn't available. Try again");
                    }else{
                        addErrorMessage(table, "There is no new log data. You need to wait!");
                    }
                }
            }
        }
    }
}