async function makeALoopWait() {
    const exampleArray = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

    const delay = async (ms = 1000) =>
        new Promise(resolve => setTimeout(resolve, ms))


    for (let i = 0; i < exampleArray.length; i += 1) {
        console.log(i)
        //put your code here.
        await delay(4000)
    }
}

makeALoopWait()